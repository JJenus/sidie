# Authentication & Authorization System — Database Design

## 1. Overview

**Users** is the identity anchor. It connects to **identities** (linked
OAuth/SSO accounts), **sessions** (active logins), **refresh_tokens** (via
sessions, for token rotation), **login_attempts** (a security log,
independent of whether login succeeded), and to **roles** through a
many-to-many `user_roles` junction. **Roles** is the RBAC hub, connecting
users to **permissions** through `role_permissions`, and scoped optionally
to an **organization** for multi-tenant setups.

```
users ──┬─< identities                 (linked OAuth/SSO accounts)
        ├─< sessions ─< refresh_tokens (rotation chain)
        ├─< login_attempts             (security log, success or fail)
        └─< roles >─┬─< permissions    (via role_permissions)
             (via     │
          user_roles) └── organizations (scopes a role, org_id nullable)
```

---

## 2. Core tables

### users
| column | type | notes |
|---|---|---|
| id | PK | |
| email | string | unique |
| password_hash | string, nullable | null for OAuth-only accounts |

### identities
| column | type | notes |
|---|---|---|
| id | PK | |
| user_id | FK -> users | |
| provider | string | e.g. `google` |
| provider_uid | string | provider's stable subject ID — key on this, never on email |
| provider_access_token | string, encrypted, nullable | only if calling the provider's API later |
| provider_refresh_token | string, encrypted, nullable | |

### organizations
| column | type | notes |
|---|---|---|
| id | PK | |
| name | string | |
| slug | string | |

### permissions
| column | type | notes |
|---|---|---|
| id | PK | |
| key | string | e.g. `billing.invoices.read` |
| description | text | |

### roles (hub)
| column | type | notes |
|---|---|---|
| id | PK | |
| name | string | |
| org_id | FK -> organizations, nullable | null = system/global role |
| description | text | |

### user_roles (junction, not drawn as a box)
| column | type | notes |
|---|---|---|
| user_id | FK -> users | composite key |
| role_id | FK -> roles | composite key |
| org_id | FK -> organizations, nullable | scope — same user can be `admin` in one org, `member` in another |

### role_permissions (junction, not drawn as a box)
| column | type | notes |
|---|---|---|
| role_id | FK -> roles | composite key |
| permission_id | FK -> permissions | composite key |

### sessions
| column | type | notes |
|---|---|---|
| id | PK | |
| user_id | FK -> users | |
| token_hash | string | never store the raw token |
| expires_at | timestamp | |

### refresh_tokens
| column | type | notes |
|---|---|---|
| id | PK | |
| session_id | FK -> sessions | |
| token_hash | string | |
| rotated_from_id | FK -> refresh_tokens, nullable | self-referencing rotation chain |
| revoked_at | timestamp, nullable | |
| expires_at | timestamp | |

### login_attempts
| column | type | notes |
|---|---|---|
| id | PK | |
| user_id | FK -> users, nullable | null for attempts against unknown/invalid emails |
| ip_address | string | |
| user_agent | string, nullable | |
| success | bool | |
| failure_reason | string, nullable | |
| attempted_at | timestamp | |

---

## 3. Design principles

- **Never store a raw credential.** Passwords, session tokens, and refresh
  tokens are all hashed before storage — a table leak shouldn't hand out
  usable credentials.
- **Key OAuth identities on the provider's stable UID, never on email.**
  Emails change and get reused; `provider_uid` doesn't.
- **Permission checks are computed, not materialized.** "Does user X have
  permission Y" is `users → user_roles → roles → role_permissions →
  permissions`, evaluated (and typically cached) at request time — don't
  hand-maintain a flat `user_permissions` table that can drift out of sync
  after a role change.
- **`org_id` nullable on `roles`** lets the same schema serve both
  single-tenant (global roles only) and multi-tenant (org-scoped roles)
  needs without a separate schema for each.
- **Separate "is logged in" from "here's the credential."** `sessions`
  represents an authenticated browser/device; `refresh_tokens` is the
  rotatable credential behind it — collapsing these into one table makes
  rotation and revocation harder to reason about.

---

## 4. OAuth / social login (e.g. Google)

- `identities.provider_uid` is the provider's stable subject ID (Google's
  `sub` claim) — this is the join key, not the email.
- Store the provider's own access/refresh tokens on the identity row only
  if you need to call that provider's API later on the user's behalf. For
  login-only integration, discard them after the initial exchange.
- **Account linking is the real design decision.** If a user signs up with
  a password using `alice@gmail.com`, then later signs in with Google
  using the same email — silently linking the two is convenient but risky:
  it trusts that Google verified the email, and it means compromising the
  Google account inherits the existing password-based account. The safer
  default is to require explicit confirmation (sign in with the existing
  password to link Google) rather than auto-merging on email match.

---

## 5. Refresh token rotation and reuse detection

Every time a refresh token is used, issue a new one and mark the old one
rotated (`rotated_from_id` points back to it) rather than allowing indefinite
reuse.

**Reuse detection** is what actually earns this pattern its value: if a
refresh token that's already been rotated (i.e. something already points to
it via `rotated_from_id`) is presented again, that's a signal of theft — the
legitimate client already moved past it, so this is a replay by someone
else. Treat this as a compromise event: revoke the entire chain back to the
originating session, not just the one token, and force re-authentication.

`sessions.expires_at` and `refresh_tokens.expires_at` serve different
purposes — the session can be long-lived in concept (weeks) while individual
refresh tokens rotate frequently and expire quickly if unused.

---

## 6. Login attempts and account-takeover detection

`login_attempts` logs every attempt, success or failure — this is a
security log, not the same thing as `sessions` (which only exists for
successful logins).

- `user_id` is nullable because a failed attempt against an unknown or
  invalid email still needs logging — this is often the more important
  case, since it's how you catch account enumeration.
- `ip_address` + `success` + `attempted_at` are the minimum for two
  different jobs:
  - **Rate limiting** — block after N failures from one IP or against one
    account within a window.
  - **Anomaly detection** — a successful login from a new country
    immediately following a string of failures elsewhere is the classic
    account-takeover pattern.
- `user_agent` (and a coarse, country-level geo lookup from IP) supports a
  common follow-on feature: "new device/location" alert emails.

**Retention note:** this table grows fast, and most of it is only useful
for a short window — rate-limit checks look back minutes to hours, not
months. Partition or prune it on a retention policy. Keep a separate,
much smaller `security_events` table (or a flag on retained rows) for the
subset worth keeping long-term: successful logins from new devices,
password changes, MFA changes — the events a user or support team might
actually need to review later.
