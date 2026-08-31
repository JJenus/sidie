package com.jjenus.tracker.userauth.application.service;

import com.jjenus.tracker.shared.exception.BusinessRuleException;
import com.jjenus.tracker.shared.exception.ValidationException;
import com.jjenus.tracker.shared.util.TimeProvider;
import com.jjenus.tracker.userauth.application.dto.LoginResponse;
import com.jjenus.tracker.userauth.application.dto.RegisterRequest;
import com.jjenus.tracker.userauth.application.dto.RegisterResponse;
import com.jjenus.tracker.userauth.application.dto.UserResponse;
import com.jjenus.tracker.userauth.domain.entity.*;
import com.jjenus.tracker.userauth.domain.enums.FailureReason;
import com.jjenus.tracker.userauth.infrastructure.repository.*;
import com.jjenus.tracker.userauth.infrastructure.security.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final SessionRepository sessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordService passwordService;
    private final TokenHashService tokenHashService;
    private final JwtService jwtService;
    private final JwtConfig jwtConfig;
    private final LoginPolicyConfig policy;
    private final Clock clock;

    public AuthService(UserRepository userRepository,
                       OrganizationRepository organizationRepository,
                       RoleRepository roleRepository,
                       PermissionRepository permissionRepository,
                       SessionRepository sessionRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       LoginAttemptRepository loginAttemptRepository,
                       PasswordService passwordService,
                       TokenHashService tokenHashService,
                       JwtService jwtService,
                       JwtConfig jwtConfig,
                       LoginPolicyConfig policy,
                       Clock clock) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.sessionRepository = sessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.passwordService = passwordService;
        this.tokenHashService = tokenHashService;
        this.jwtService = jwtService;
        this.jwtConfig = jwtConfig;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request, String ip, String userAgent) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ValidationException("EMAIL_TAKEN", "email already registered");
        }

        Organization org = Organization.create(request.getOrganizationName(), generateSlug(request.getOrganizationName()));
        organizationRepository.save(org);
        seedOrgRoles(org);

        User user = new User();
        user.setEmail(request.getEmail().toLowerCase(Locale.ROOT));
        user.setPasswordHash(passwordService.hash(request.getPassword()));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        userRepository.save(user);

        Role tenantAdmin = roleRepository
            .findByNameAndOrgId("TENANT_ADMIN", org.getId())
            .orElseThrow(() -> new BusinessRuleException("ROLE_MISSING", "TENANT_ADMIN role missing"));
        user.assignRole(tenantAdmin);
        userRepository.save(user);

        IssuedTokens issued = issueTokens(user, org.getId());
        loginAttemptRepository.save(LoginAttempt.success(user, ip, userAgent));

        return new RegisterResponse(
            user.getId(), org.getId(), issued.accessToken(),
            issued.refreshToken(), jwtService.getAccessTokenExpirySeconds()
        );
    }

    @Transactional
    public LoginResponse login(String email, String password, String ip, String userAgent) {
        Optional<User> userOpt = userRepository.findByEmail(email.toLowerCase(Locale.ROOT));
        User user = userOpt.orElse(null);

        if (user == null) {
            loginAttemptRepository.save(LoginAttempt.failure(null, ip, userAgent, FailureReason.USER_NOT_FOUND));
            throw new AuthException("INVALID_CREDENTIALS", "invalid email or password");
        }

        Instant now = Instant.now(clock);
        long recentFailures = loginAttemptRepository.countByUserIdAndSuccessFalseAndAttemptedAtAfter(
            user.getId(), now.minus(policy.getFailedAttemptsWindow()));
        if (recentFailures >= policy.getMaxFailedAttempts()) {
            user.lock();
            userRepository.save(user);
            loginAttemptRepository.save(LoginAttempt.failure(user, ip, userAgent, FailureReason.ACCOUNT_LOCKED));
            throw new AuthException("ACCOUNT_LOCKED", "account is temporarily locked");
        }

        if (user.isLocked()) {
            loginAttemptRepository.save(LoginAttempt.failure(user, ip, userAgent, FailureReason.USER_LOCKED));
            throw new AuthException("ACCOUNT_LOCKED", "account is locked");
        }
        if (!user.isEnabled()) {
            loginAttemptRepository.save(LoginAttempt.failure(user, ip, userAgent, FailureReason.USER_DISABLED));
            throw new AuthException("USER_DISABLED", "user is disabled");
        }
        if (!passwordService.matches(password, user.getPasswordHash())) {
            loginAttemptRepository.save(LoginAttempt.failure(user, ip, userAgent, FailureReason.INVALID_CREDENTIALS));
            long newFailures = loginAttemptRepository.countByUserIdAndSuccessFalseAndAttemptedAtAfter(
                user.getId(), now.minus(policy.getFailedAttemptsWindow()));
            if (newFailures >= policy.getMaxFailedAttempts()) {
                user.lock();
                userRepository.save(user);
            }
            throw new AuthException("INVALID_CREDENTIALS", "invalid email or password");
        }

        IssuedTokens issued = issueTokens(user, primaryOrgId(user));
        loginAttemptRepository.save(LoginAttempt.success(user, ip, userAgent));
        return new LoginResponse(issued.accessToken(), issued.refreshToken(),
            jwtService.getAccessTokenExpirySeconds());
    }

    @Transactional
    public LoginResponse refresh(String rawRefreshToken, String ip, String userAgent) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new AuthException("INVALID_TOKEN", "refresh token required");
        }
        String hash = tokenHashService.hash(rawRefreshToken);
        Instant now = Instant.now(clock);
        RefreshToken existing = refreshTokenRepository.findActiveByHash(hash, now)
            .orElseThrow(() -> {
                loginAttemptRepository.save(LoginAttempt.failure(null, ip, userAgent, FailureReason.INVALID_TOKEN));
                return new AuthException("INVALID_TOKEN", "refresh token invalid or expired");
            });

        if (existing.hasBeenRotated()) {
            revokeChain(existing);
            loginAttemptRepository.save(LoginAttempt.failure(existing.getSession().getUser(), ip, userAgent, FailureReason.REUSED_TOKEN));
            throw new com.jjenus.tracker.userauth.infrastructure.security.SecurityException("REUSED_TOKEN", "refresh token reuse detected; session revoked");
        }

        Session session = existing.getSession();
        User user = session.getUser();

        existing.revoke();
        refreshTokenRepository.save(existing);

        IssuedTokens issued = rotateTokens(user, primaryOrgId(user), session, existing);
        return new LoginResponse(issued.accessToken(), issued.refreshToken(),
            jwtService.getAccessTokenExpirySeconds());
    }

    @Transactional
    public void logout(Long userId, String ip, String userAgent) {
        if (userId == null) return;
        List<Session> all = sessionRepository.findAll();
        Instant now = Instant.now(clock);
        for (Session s : all) {
            if (s.getUser().getId().equals(userId) && s.getRevokedAt() == null) {
                s.revokeAt(now);
                sessionRepository.save(s);
                for (RefreshToken rt : refreshTokenRepository.findBySessionId(s.getId())) {
                    if (rt.getRevokedAt() == null) {
                        rt.revokeAt(now);
                        refreshTokenRepository.save(rt);
                    }
                }
            }
        }
    }

    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword, String ip, String userAgent) {
        if (userId == null) {
            throw new AuthException("UNAUTHENTICATED", "authentication required");
        }
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessRuleException("USER_NOT_FOUND", "user not found"));
        if (!passwordService.matches(oldPassword, user.getPasswordHash())) {
            loginAttemptRepository.save(LoginAttempt.failure(user, ip, userAgent, FailureReason.INVALID_CREDENTIALS));
            throw new AuthException("INVALID_CREDENTIALS", "current password incorrect");
        }
        passwordService.validateStrength(newPassword);
        user.setPasswordHash(passwordService.hash(newPassword));
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessRuleException("USER_NOT_FOUND", "user not found"));
        return toResponse(user);
    }

    @Transactional
    public UserResponse createUser(String email, String password, String firstName, String lastName, Long orgId) {
        if (userRepository.existsByEmail(email.toLowerCase(Locale.ROOT))) {
            throw new ValidationException("EMAIL_TAKEN", "email already registered");
        }
        User u = new User();
        u.setEmail(email.toLowerCase(Locale.ROOT));
        u.setPasswordHash(passwordService.hash(password));
        u.setFirstName(firstName);
        u.setLastName(lastName);
        userRepository.save(u);
        if (orgId != null) {
            Role role = roleRepository.findByNameAndOrgId("OPERATOR", orgId).orElse(null);
            if (role != null) {
                u.assignRole(role);
                userRepository.save(u);
            }
        }
        return toResponse(u);
    }

    @Transactional
    public UserResponse assignRoles(Long userId, List<Long> roleIds, Long orgId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessRuleException("USER_NOT_FOUND", "user not found"));
        user.clearRoles();
        for (Long roleId : roleIds) {
            Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessRuleException("ROLE_NOT_FOUND", "role " + roleId + " not found"));
            if (role.isOrgScoped() && !Objects.equals(role.getOrg().getId(), orgId)) {
                throw new ValidationException("ROLE_ORG_MISMATCH",
                    "role " + role.getName() + " is not scoped to org " + orgId);
            }
            user.assignRole(role);
        }
        userRepository.save(user);
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsersInOrg(Long orgId) {
        return userRepository.findByOrgId(orgId,
                org.springframework.data.domain.PageRequest.of(0, 1000))
            .map(this::toResponse)
            .getContent();
    }

    private void seedOrgRoles(Organization org) {
        Map<String, List<String>> rolePerms = Map.of(
            "TENANT_ADMIN", List.of("users.read", "users.write", "users.delete", "users.assign_roles",
                "organizations.read", "roles.read", "roles.write",
                "vehicles.read", "vehicles.write", "vehicles.assign_device",
                "alerts.read", "alerts.write", "devices.read", "devices.command"),
            "OPERATOR", List.of("users.read", "vehicles.read", "alerts.read", "alerts.write",
                "trips.read", "notifications.read", "notifications.write"),
            "VIEWER", List.of("vehicles.read", "alerts.read", "trips.read")
        );

        for (Map.Entry<String, List<String>> e : rolePerms.entrySet()) {
            Role role = Role.orgRole(org, e.getKey(), e.getKey() + " for org " + org.getSlug());
            for (String key : e.getValue()) {
                Permission p = permissionRepository.findByKey(key).orElseGet(() -> {
                    Permission created = Permission.of(key, key);
                    permissionRepository.save(created);
                    return created;
                });
                role.addPermission(p);
            }
            roleRepository.save(role);
        }
    }

    private IssuedTokens issueTokens(User user, Long orgId) {
        String sessionRaw = tokenHashService.generateOpaqueToken();
        String sessionHash = tokenHashService.hash(sessionRaw);
        Instant sessionExpires = Instant.now(clock).plus(jwtConfig.getAccessTokenExpiry());
        Session session = Session.create(user, sessionHash, sessionExpires);
        sessionRepository.save(session);

        String refreshRaw = tokenHashService.generateOpaqueToken();
        String refreshHash = tokenHashService.hash(refreshRaw);
        Instant refreshExpires = Instant.now(clock).plus(jwtConfig.getRefreshTokenExpiry());
        RefreshToken refresh = RefreshToken.issue(session, refreshHash, refreshExpires);
        refreshTokenRepository.save(refresh);

        String access = generateAccessToken(user, orgId);
        return new IssuedTokens(access, refreshRaw);
    }

    private IssuedTokens rotateTokens(User user, Long orgId, Session session, RefreshToken oldToken) {
        String refreshRaw = tokenHashService.generateOpaqueToken();
        String refreshHash = tokenHashService.hash(refreshRaw);
        Instant refreshExpires = Instant.now(clock).plus(jwtConfig.getRefreshTokenExpiry());
        RefreshToken next = RefreshToken.rotateFrom(session, oldToken, refreshHash, refreshExpires);
        refreshTokenRepository.save(next);
        String access = generateAccessToken(user, orgId);
        return new IssuedTokens(access, refreshRaw);
    }

    private String generateAccessToken(User user, Long orgId) {
        List<String> roleNames = user.getRoles().stream().map(Role::getName).distinct().collect(Collectors.toList());
        return jwtService.generateAccessToken(user.getId(), orgId, user.getEmail(), roleNames);
    }

    private UserResponse toResponse(User u) {
        UserResponse r = new UserResponse();
        r.setId(u.getId());
        r.setEmail(u.getEmail());
        r.setFirstName(u.getFirstName());
        r.setLastName(u.getLastName());
        r.setEnabled(u.isEnabled());
        r.setLocked(u.isLocked());
        r.setCreatedAt(u.getCreatedAt());
        r.setRoles(u.getRoles().stream().map(Role::getName).collect(Collectors.toList()));
        u.getRoles().stream()
            .filter(Role::isOrgScoped)
            .findFirst()
            .ifPresent(role -> r.setOrganizationId(role.getOrg().getId()));
        return r;
    }

    private Long primaryOrgId(User user) {
        return user.getRoles().stream()
            .filter(Role::isOrgScoped)
            .findFirst()
            .map(role -> role.getOrg().getId())
            .orElse(null);
    }

    private void revokeChain(RefreshToken reused) {
        RefreshToken current = reused;
        Set<Long> visited = new HashSet<>();
        Instant now = Instant.now(clock);
        while (current != null && visited.add(current.getId())) {
            if (current.getRevokedAt() == null) {
                current.revokeAt(now);
                refreshTokenRepository.save(current);
            }
            current = current.getRotatedFrom();
        }
        Session session = reused.getSession();
        if (session != null && session.getRevokedAt() == null) {
            session.revokeAt(now);
            sessionRepository.save(session);
        }
    }

    private String generateSlug(String name) {
        String base = name.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9-]+", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
        if (base.length() < 3) base = (base + "-org").substring(0, Math.min(100, base.length() + 4));
        if (base.length() > 100) base = base.substring(0, 100);
        if (organizationRepository.existsBySlug(base)) {
            base = base.substring(0, Math.min(95, base.length())) + "-" + TimeProvider.newId().substring(0, 4);
        }
        return base;
    }

    private record IssuedTokens(String accessToken, String refreshToken) {}
}
