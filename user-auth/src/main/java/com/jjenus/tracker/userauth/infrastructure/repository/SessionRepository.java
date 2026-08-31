package com.jjenus.tracker.userauth.infrastructure.repository;

import com.jjenus.tracker.userauth.domain.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {

    @Query("SELECT s FROM Session s WHERE s.tokenHash = :tokenHash AND s.expiresAt > :now AND s.revokedAt IS NULL")
    Optional<Session> findByTokenHashAndExpiresAtAfter(@Param("tokenHash") String tokenHash,
                                                        @Param("now") Instant now);
}
