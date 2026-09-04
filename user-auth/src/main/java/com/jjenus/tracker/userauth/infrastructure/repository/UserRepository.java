package com.jjenus.tracker.userauth.infrastructure.repository;

import com.jjenus.tracker.userauth.domain.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.org.id = :orgId")
    Page<User> findByOrgId(@Param("orgId") Long orgId, Pageable pageable);

    @Query("SELECT DISTINCT u FROM User u JOIN u.roles r WHERE u.id = :userId AND r.org.id = :orgId")
    Optional<User> findByIdAndOrgId(@Param("userId") Long userId, @Param("orgId") Long orgId);
}
