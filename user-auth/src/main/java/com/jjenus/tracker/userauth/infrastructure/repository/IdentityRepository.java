package com.jjenus.tracker.userauth.infrastructure.repository;

import com.jjenus.tracker.userauth.domain.entity.Identity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdentityRepository extends JpaRepository<Identity, Long> {
    Optional<Identity> findByProviderAndProviderUid(String provider, String providerUid);
}
