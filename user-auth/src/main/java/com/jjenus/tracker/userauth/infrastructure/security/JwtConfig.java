package com.jjenus.tracker.userauth.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "userauth.jwt")
public class JwtConfig {

    private String secret;
    private Duration accessTokenExpiry = Duration.ofHours(1);
    private Duration refreshTokenExpiry = Duration.ofHours(168);
    private String issuer = "tracker-auth";

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public Duration getAccessTokenExpiry() { return accessTokenExpiry; }
    public void setAccessTokenExpiry(Duration accessTokenExpiry) { this.accessTokenExpiry = accessTokenExpiry; }

    public Duration getRefreshTokenExpiry() { return refreshTokenExpiry; }
    public void setRefreshTokenExpiry(Duration refreshTokenExpiry) { this.refreshTokenExpiry = refreshTokenExpiry; }

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
}
