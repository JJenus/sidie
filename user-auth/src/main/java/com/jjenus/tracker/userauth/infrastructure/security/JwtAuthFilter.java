package com.jjenus.tracker.userauth.infrastructure.security;

import com.jjenus.tracker.shared.security.TenantContext;
import com.jjenus.tracker.userauth.infrastructure.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractBearer(request);
        if (token != null) {
            try {
                Jws<Claims> parsed = jwtService.parseAndValidate(token);
                Claims claims = parsed.getPayload();
                Long userId = Long.valueOf(claims.getSubject());
                Long orgId = readLong(claims, JwtService.CLAIM_ORG_ID);
                String email = claims.get(JwtService.CLAIM_EMAIL, String.class);
                @SuppressWarnings("unchecked")
                List<String> roles = claims.get(JwtService.CLAIM_ROLES, List.class);
                if (roles == null) roles = List.of();

                List<SimpleGrantedAuthority> authorities = roles.stream()
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                    .collect(Collectors.toList());

                AuthenticatedUser principal = new AuthenticatedUser(userId, email, orgId, roles);
                UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);

                TenantContext.setCurrentUserId(userId);
                TenantContext.setCurrentOrgId(orgId);
            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String extractBearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private Long readLong(Claims claims, String key) {
        Object value = claims.get(key);
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public record AuthenticatedUser(Long userId, String email, Long orgId, List<String> roles) {
    }
}
