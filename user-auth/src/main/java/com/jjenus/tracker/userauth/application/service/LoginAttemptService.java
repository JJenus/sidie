package com.jjenus.tracker.userauth.application.service;

import com.jjenus.tracker.userauth.domain.entity.LoginAttempt;
import com.jjenus.tracker.userauth.infrastructure.repository.LoginAttemptRepository;
import com.jjenus.tracker.userauth.infrastructure.security.LoginPolicyConfig;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class LoginAttemptService {

    private final LoginAttemptRepository loginAttemptRepository;
    private final LoginPolicyConfig policy;
    private final Clock clock;

    public LoginAttemptService(LoginAttemptRepository loginAttemptRepository,
                              LoginPolicyConfig policy,
                              Clock clock) {
        this.loginAttemptRepository = loginAttemptRepository;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean isRateLimited(Long userId) {
        if (userId == null) return false;
        Instant since = Instant.now(clock).minus(policy.getFailedAttemptsWindow());
        return loginAttemptRepository.countByUserIdAndSuccessFalseAndAttemptedAtAfter(userId, since)
            >= policy.getMaxFailedAttempts();
    }

    @Transactional(readOnly = true)
    public boolean isIpRateLimited(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) return false;
        Instant since = Instant.now(clock).minus(policy.getFailedAttemptsWindow());
        return loginAttemptRepository.countByIpAddressAndSuccessFalseAndAttemptedAtAfter(ipAddress, since)
            >= policy.getMaxFailedAttempts();
    }

    @Transactional(readOnly = true)
    public long recentFailedAttempts(Long userId) {
        if (userId == null) return 0;
        Instant since = Instant.now(clock).minus(policy.getFailedAttemptsWindow());
        return loginAttemptRepository.countByUserIdAndSuccessFalseAndAttemptedAtAfter(userId, since);
    }

    @Transactional(readOnly = true)
    public List<LoginAttempt> recentAttempts(Long userId, int limit) {
        return loginAttemptRepository.findAll().stream()
            .filter(a -> a.getUser() != null && a.getUser().getId().equals(userId))
            .sorted((a, b) -> b.getAttemptedAt().compareTo(a.getAttemptedAt()))
            .limit(limit)
            .toList();
    }
}
