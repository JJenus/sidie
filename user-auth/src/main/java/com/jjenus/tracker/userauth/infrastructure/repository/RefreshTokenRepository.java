package com.jjenus.tracker.userauth.infrastructure.repository;

import com.jjenus.tracker.userauth.domain.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenHash = :hash AND rt.revokedAt IS NULL AND rt.expiresAt > :now")
    Optional<RefreshToken> findActiveByHash(@Param("hash") String hash, @Param("now") Instant now);

    List<RefreshToken> findBySessionId(Long sessionId);
}
