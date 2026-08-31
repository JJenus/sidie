# Ride-Hailing System — Complete Database Design

## 1. Overview

Core loop: a **rider** requests a ride → **dispatch** matches a **driver**
→ the ride runs, tracked live → it's **priced** (base + surge + promo) →
**paid** → **rated** → and the driver is **paid out** later, on a separate
schedule.

```
users ──┬─< payment_methods            ├─< saved_places
        ├─< drivers ─┬─< vehicles       └─< rides ─┬─< dispatch_attempts >─ drivers
        │            ├─< driver_locations           ├─< ride_stops
        │            ├─< driver_documents            ├─< ride_locations
        │            └─< driver_earnings ─< payouts  ├─< ride_events (audit)
        │                                             ├─< ratings
        └─< rides (as rider)                          ├─< fare_line_items
                                                        ├─< sos_events / trip_shares
                                                        └─< payments ─< refunds
                                        promotions ─< promo_redemptions
```

Two money flows run through this schema, kept deliberately separate:
**rider → platform** (payments) and **platform → driver** (payouts). A
rider's card can decline while the driver still completed the trip and is
owed for it — one table can't represent both sides failing independently.

---

## 2. Core identity and vehicle tables

### users
| column | type | notes |
|---|---|---|
| id | PK | |
| email | string | |
| phone | string | |
| default_currency | char(3) | |

### drivers
| column | type | notes |
|---|---|---|
| id | PK | |
| user_id | FK -> users | 1:1 extension, same pattern as `identities` extending `users` in the auth system |
| status | enum | `pending_verification`, `active`, `suspended`, `deactivated` — derived from `driver_documents` state, not set independently |

### vehicles
| column | type | notes |
|---|---|---|
| id | PK | |
| driver_id | FK -> drivers | |
| plate | string | |
| ride_type_id | FK -> ride_types | what category this vehicle qualifies for |
| is_active | bool | |

### payment_methods
| column | type | notes |
|---|---|---|
| id | PK | |
| user_id | FK -> users | riders only — driver payouts go to bank details, a separate concern |
| type | enum | |
| last4 | string | |

### ratings
| column | type | notes |
|---|---|---|
| id | PK | |
| ride_id | FK -> rides | |
| rater_id | FK -> users | |
| ratee_id | FK -> users | who's being rated — makes both directions queryable from one table (`WHERE ratee_id = X` for a driver's average) |
| score | int | |
| comment | text, nullable | |

---

## 3. Rides (the hub)

### rides
| column | type | notes |
|---|---|---|
| id | PK | |
| idempotency_key | string, unique, nullable | client-supplied — prevents duplicate ride creation on a retried request |
| rider_id | FK -> users | |
| driver_id | FK -> drivers, nullable | null until matched |
| vehicle_id | FK -> vehicles, nullable | |
| ride_type_id | FK -> ride_types | |
| status | enum | see §5 |
| pickup_lat_lng | point | |
| dropoff_lat_lng | point | |
| requested_at | timestamp | |
| canceled_by | enum, nullable | `rider`, `driver`, `system` |
| cancellation_reason | string, nullable | |

### ride_stops (multi-stop rides)
| column | type | notes |
|---|---|---|
| id | PK | |
| ride_id | FK -> rides | |
| sequence | int | order of the stop |
| lat_lng | point | |
| arrived_at | timestamp, nullable | |

### saved_places
| column | type | notes |
|---|---|---|
| id | PK | |
| user_id | FK -> users | |
| label | string | "Home", "Work" |
| lat_lng | point | |

---

## 4. Dispatch and live matching

The piece easy to miss initially: "find available drivers near this
pickup point" happens *before* any ride exists.

### driver_locations
| column | type | notes |
|---|---|---|
| driver_id | PK, FK -> drivers | one current row per driver — upserted, not appended |
| lat_lng | geography point | needs a geospatial index (e.g. PostGIS GIST) — "drivers within X km" is a fundamentally different query shape than anything else in this schema |
| status | enum | `online`, `on_trip`, `offline` |
| updated_at | timestamp | |

At real scale, this table's write volume (every driver, every few seconds)
and read pattern (radius search) diverge enough from the rest of the
schema that it's a strong candidate to live in a separate store (Redis geo
commands, or a dedicated matching service), with Postgres as system of
record for everything else.

### dispatch_attempts
| column | type | notes |
|---|---|---|
| id | PK | |
| ride_id | FK -> rides | |
| driver_id | FK -> drivers | |
| offered_at | timestamp | |
| responded_at | timestamp, nullable | |
| response | enum | `accepted`, `declined`, `timed_out` |

The audit trail of matching itself. This is what lets you reconstruct "why
did this rider wait 4 minutes" after the fact, and it's the dataset used
to tune matching radius or timeout over time.

### ride_locations (live GPS trail, once a ride exists)
| column | type | notes |
|---|---|---|
| id | PK | |
| ride_id | FK -> rides | |
| lat_lng | point | |
| recorded_at | timestamp | |

The fastest-growing table in the schema (a 20-minute ride at a 5-second
ping interval is ~240 rows) — first candidate for time-based partitioning
or a purpose-built time-series store as volume grows.

---

## 5. Ride status lifecycle and audit trail

```
requested → matched → arriving → in_progress → completed
requested/matched/arriving → canceled (by rider, driver, or system timeout)
```

### ride_events
| column | type | notes |
|---|---|---|
| id | PK | |
| ride_id | FK -> rides | |
| from_status | enum | |
| to_status | enum | |
| actor | enum | `rider`, `driver`, `system` |
| occurred_at | timestamp | |

Same pattern as the subscription and auth systems: log every transition
rather than relying on the current status column alone — "the driver
marked this in_progress at 14:02, three minutes before pickup" is only
answerable from an event log.

---

## 6. Fare calculation and pricing

### ride_types
| column | type | notes |
|---|---|---|
| id | PK | |
| name | string | economy, xl, premium |
| base_fare | numeric | |
| per_km_rate | numeric | |
| per_min_rate | numeric | |
| capacity | int | |

### fare_line_items
| column | type | notes |
|---|---|---|
| id | PK | |
| ride_id | FK -> rides | |
| type | enum | `base`, `distance`, `time`, `surge`, `promo_discount`, `toll`, `tip`, `cancellation_fee`, `platform_fee` |
| amount | numeric | negative for discounts |

A fare is a sum of these, never a single stored number — same reasoning as
invoice line items in the subscription system, applied to a one-off charge
instead of a recurring one. Surge is logged at the multiplier active *at
request time*, not recomputed later, so a fare stays reconstructable and
disputable after the fact. Cancellation fees are logged here too, computed
from how far into the flow the cancellation happened.

---

## 7. Promotions

### promotions
| column | type | notes |
|---|---|---|
| id | PK | |
| code | string, unique | |
| discount_type | enum | `percent`, `fixed_amount` |
| discount_value | numeric | |
| max_redemptions | int, nullable | |
| redemptions_count | int | denormalized cache — see coupon design pattern for the atomic-update guard against overshoot |
| max_redemptions_per_user | int, nullable | |
| expires_at | timestamp, nullable | |

### promo_redemptions
| column | type | notes |
|---|---|---|
| id | PK | |
| promotion_id | FK -> promotions | |
| user_id | FK -> users | |
| ride_id | FK -> rides | |
| redeemed_at | timestamp | |

The ledger, not just the counter — same reasoning as the subscription
system's coupon design: `redemptions_count` is a fast-read cache,
`promo_redemptions` is what actually enforces per-user limits and
provides an audit trail.

---

## 8. Billing — rider payments

### payments (rider → platform)
| column | type | notes |
|---|---|---|
| id | PK | |
| ride_id | FK -> rides | |
| payment_method_id | FK -> payment_methods | |
| idempotency_key | string, unique | prevents double-charging on a retried capture request |
| gateway_transaction_id | string | |
| authorized_amount | numeric | the hold amount, at request time |
| captured_amount | numeric | the final amount actually charged, at completion |
| currency | char(3) | |
| status | enum | `authorized`, `captured`, `failed`, `refunded` |
| authorized_at | timestamp | |
| captured_at | timestamp, nullable | |

Splitting `authorized_amount` from `captured_amount` matters: the estimate
at request time and the real fare at completion are routinely different
(traffic, wait time, route change) — collapsing them into one `amount`
column loses the ability to see how far off the estimate was.

### refunds
| column | type | notes |
|---|---|---|
| id | PK | |
| payment_id | FK -> payments | |
| amount | numeric | |
| reason | text | |
| status | enum | `requested`, `approved`, `processed`, `denied` |
| requested_by | enum | `rider`, `support`, `system` |
| created_at | timestamp | |

Never mutate the original `payments` row for a refund — insert a new row
referencing it. The payment record should always reflect what was actually
charged at the time; the refund is a separate, later, additive event.

**Rider charge flow, step by step:**
1. **Ride requested** — fare estimate shown (from `ride_types` base rates +
   current surge). Nothing charged or held yet.
2. **Authorization hold** — once matched, hold roughly the estimated
   amount on the rider's `payment_method`. `payments` row created,
   `status = 'authorized'`.
3. **Ride completed → fare finalized** — `fare_line_items` rows written.
   This is the moment the real total exists.
4. **Capture** — charge the hold for the *finalized* total, not the
   original estimate. `captured_amount` set, `status = 'captured'`. If the
   final fare exceeds the hold, most processors allow capturing somewhat
   over it before requiring a fresh authorization.
5. **Branch:**
   - **Succeeded** → immediately create the `driver_earnings` row for the
     ride — this feeds the payout batch.
   - **Failed** → don't retry inline. Flag the ride unpaid, run a
     background retry job with backoff; if exhausted, apply a collections
     flag that can block future ride requests until resolved.

---

## 9. Billing — driver earnings and payouts

### driver_earnings (per-ride ledger)
| column | type | notes |
|---|---|---|
| id | PK | |
| ride_id | FK -> rides | |
| driver_id | FK -> drivers | |
| gross_fare | numeric | |
| platform_commission | numeric | |
| net_earning | numeric | gross − commission |
| payout_id | FK -> payouts, nullable | null until batched into a payout |

### payouts (platform → driver)
| column | type | notes |
|---|---|---|
| id | PK | |
| driver_id | FK -> drivers | |
| period_start | timestamp | |
| period_end | timestamp | |
| amount | numeric | sum of `driver_earnings.net_earning` for the period |
| status | enum | `pending`, `in_transit`, `paid`, `failed`, `held_for_review` |
| gateway_transfer_id | string | |
| transferred_at | timestamp, nullable | |

**Driver payout flow, step by step:**
1. **Accrual** — `driver_earnings` rows build up per completed ride,
   `payout_id` left null. No transfer happens yet.
2. **Scheduled batch job** (daily/weekly, not per-ride) — sums all
   unbatched `driver_earnings` for a driver over the period, creates one
   `payouts` row (`status = 'pending'`), sets `payout_id` on each included
   earnings row. Batching is deliberate: transfers cost money and take
   time, and a driver's payout should be one stable, reviewable total.
3. **Transfer initiated** — `status = 'in_transit'`,
   `gateway_transfer_id` recorded for reconciliation.
4. **Branch:**
   - **Succeeded** → `status = 'paid'`, `transferred_at` set.
   - **Failed** (bad bank details, closed account) → `status =
     'held_for_review'`. Do **not** re-batch the underlying
     `driver_earnings` into the next period — they're already linked to
     this `payout_id`; re-batching would double-count them. Hold this
     specific payout for review, retry the same row once details are
     fixed.

**Currency:** `users.default_currency`, `payments.currency`, and
`ride_types` pricing all need to agree on which currency a ride is priced
in — store currency explicitly on `payments` at the point of charge, never
assume one global currency once operating across regions.

---

## 10. Safety and driver compliance

### sos_events
| column | type | notes |
|---|---|---|
| id | PK | |
| ride_id | FK -> rides | |
| triggered_by | FK -> users | |
| triggered_at | timestamp | |
| resolved_at | timestamp, nullable | |
| notes | text, nullable | |

### trip_shares
| column | type | notes |
|---|---|---|
| id | PK | |
| ride_id | FK -> rides | |
| share_token | string, unique | unguessable — lets a non-user view live trip status via a link |
| shared_with_contact | string | phone or email, not necessarily a user |
| created_at | timestamp | |
| expires_at | timestamp | tied to ride completion, not indefinite |

### driver_documents
| column | type | notes |
|---|---|---|
| id | PK | |
| driver_id | FK -> drivers | |
| type | enum | `license`, `insurance`, `vehicle_registration`, `background_check` |
| status | enum | `pending`, `verified`, `expired`, `rejected` |
| expires_at | timestamp, nullable | |
| verified_at | timestamp, nullable | |

A driver's `status = 'active'` should be *derived* from having all
required `driver_documents` verified and unexpired — not set
independently, or a driver with lapsed insurance can keep driving because
nothing forced the two facts to stay in sync.

---

## 11. Design principles carried through this design

- **Line items over flat totals** wherever money is calculated
  (`fare_line_items`) — auditable and disputable after the fact.
- **Ledgers over live counters** for anything enforced
  (`promo_redemptions`, `driver_earnings`) — the counter is a fast-read
  cache, the ledger is the audit trail.
- **Event logs over status-only columns** (`ride_events`) — current state
  is derived from the log, never the only record of what happened.
- **Idempotency keys at every retry-prone write** (ride creation,
  payment capture) — mobile clients on unreliable networks make this
  routine, not rare.
- **Two directions of money, two tables** — `payments` and `payouts` never
  share a table or mirror each other; they fail independently and have
  different timing.
- **Authorized vs. captured amount, kept separate** — the estimate and the
  real charge differ for a reason (the ride happened in between); the
  schema should show that gap, not collapse it.
- **Payout batching is idempotent by construction** — once a
  `driver_earnings` row has a `payout_id`, it's spoken for; a failed
  transfer holds that specific payout rather than silently re-entering
  the earnings into a future batch.
- **Don't materialize what's derivable** — a driver's average rating is
  computed from `ratings`, cached asynchronously for read performance if
  needed, not maintained by hand on every new rating.
