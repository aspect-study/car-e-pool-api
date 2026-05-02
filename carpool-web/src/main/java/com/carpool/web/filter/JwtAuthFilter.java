package com.carpool.web.filter;

import com.carpool.service.auth.JwtService;
import com.carpool.web.security.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs once per request. Extracts JWT from Authorization header,
 * validates it, and populates the SecurityContext.
 *
 * If the token is missing or invalid, the request proceeds unauthenticated —
 * Spring Security's access rules will then reject it at the endpoint level.
 * This separation keeps auth and authz concerns clean.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_HEADER   = "Authorization";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtService.validateAndParseClaims(token);

                Long   userId     = jwtService.extractUserId(claims);
                Long   telegramId = jwtService.extractTelegramId(claims);

                // Role is read from JWT claim — not from DB on every request.
                // If a user's role was updated, their existing token retains the old role
                // until expiry. This is intentional — stateless JWT design trade-off.
                String role = jwtService.extractRole(claims);

                AuthenticatedUser principal = new AuthenticatedUser(userId, telegramId, role);

                // No credentials needed — token IS the credential
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities());

                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (JwtException e) {
                // Invalid token — log at debug level, don't expose details
                log.debug("JWT validation failed: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the raw token from "Authorization: Bearer <token>" header.
     * Returns null if header is absent or malformed.
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
