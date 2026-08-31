package com.jjenus.tracker.userauth.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    public static final String CLAIM_ORG_ID = "org_id";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLES = "roles";

    private final JwtConfig config;
    private final SecretKey signingKey;
    private final Clock clock;

    public JwtService(JwtConfig config) {
        this(config, Clock.systemUTC());
    }

    public JwtService(JwtConfig config, Clock clock) {
        this.config = config;
        if (config.getSecret() == null || config.getSecret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("userauth.jwt.secret must be at least 32 bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(config.getSecret().getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    public String generateAccessToken(Long userId, Long orgId, String email, List<String> roles) {
        Instant now = Instant.now(clock);
        Instant expiry = now.plus(config.getAccessTokenExpiry());

        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_ORG_ID, orgId);
        claims.put(CLAIM_EMAIL, email);
        claims.put(CLAIM_ROLES, roles);

        return Jwts.builder()
            .subject(String.valueOf(userId))
            .issuer(config.getIssuer())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .id(UUID.randomUUID().toString())
            .claims(claims)
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact();
    }

    public Jws<Claims> parseAndValidate(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .clock(() -> Date.from(Instant.now(clock)))
            .build()
            .parseSignedClaims(token);
    }

    public Instant getAccessTokenExpiryAsInstant() {
        return Instant.now(clock).plus(config.getAccessTokenExpiry());
    }

    public long getAccessTokenExpirySeconds() {
        return config.getAccessTokenExpiry().toSeconds();
    }
}
