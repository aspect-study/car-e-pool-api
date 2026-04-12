package com.carpool.service.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Handles JWT creation and validation.
 * Tokens are signed with HMAC-SHA256 using the configured secret.
 *
 * Claims stored in token:
 *   sub  = internal user ID (Long, as String)
 *   tgid = Telegram ID — allows quick identity check without DB query
 *   role = user role — avoids DB lookup on every request
 */
@Slf4j
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${carpool.jwt.secret}") String secret,
            @Value("${carpool.jwt.expiration-ms}") long expirationMs) {
        // Key must be at least 256 bits for HMAC-SHA256
        this.signingKey  = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * Generate a signed JWT for the authenticated user.
     */
    public String generateToken(Long userId, Long telegramId, String role) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("tgid", telegramId)
                .claim("role", role)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validate token signature and expiry. Returns parsed claims on success.
     * Throws JwtException (unchecked) on any validation failure.
     */
    public Claims validateAndParseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long extractUserId(Claims claims) {
        return Long.parseLong(claims.getSubject());
    }

    public Long extractTelegramId(Claims claims) {
        return claims.get("tgid", Long.class);
    }

    public String extractRole(Claims claims) {
        return claims.get("role", String.class);
    }

    public boolean isTokenValid(String token) {
        try {
            validateAndParseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
