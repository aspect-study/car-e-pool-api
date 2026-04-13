package com.carpool.service.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtService")
class JwtServiceTest {

    // 32-char secret = 256 bits — minimum for HMAC-SHA256
    private static final String SECRET      = "test-secret-key-32chars-minimum!";
    private static final long   EXPIRY_MS   = 3_600_000L; // 1 hour

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, EXPIRY_MS);
    }

    @Test
    @DisplayName("should generate a non-blank token")
    void shouldGenerateToken() {
        String token = jwtService.generateToken(1L, 111L, "DRIVER");
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("should extract correct claims from valid token")
    void shouldExtractCorrectClaims() {
        String token = jwtService.generateToken(42L, 999L, "PASSENGER");

        Claims claims = jwtService.validateAndParseClaims(token);

        assertThat(jwtService.extractUserId(claims)).isEqualTo(42L);
        assertThat(jwtService.extractTelegramId(claims)).isEqualTo(999L);
        assertThat(jwtService.extractRole(claims)).isEqualTo("PASSENGER");
    }

    @Test
    @DisplayName("should return true for a valid token")
    void shouldReturnTrueForValidToken() {
        String token = jwtService.generateToken(1L, 111L, "DRIVER");
        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    @DisplayName("should return false for a tampered token")
    void shouldReturnFalseForTamperedToken() {
        String token = jwtService.generateToken(1L, 111L, "DRIVER");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(jwtService.isTokenValid(tampered)).isFalse();
    }

    @Test
    @DisplayName("should return false for an expired token")
    void shouldReturnFalseForExpiredToken() {
        // Create service with -1ms expiry — token is expired immediately
        JwtService expiredService = new JwtService(SECRET, -1L);
        String token = expiredService.generateToken(1L, 111L, "DRIVER");
        assertThat(expiredService.isTokenValid(token)).isFalse();
    }

    @Test
    @DisplayName("should return false for a completely invalid string")
    void shouldReturnFalseForGarbage() {
        assertThat(jwtService.isTokenValid("not.a.jwt")).isFalse();
        assertThat(jwtService.isTokenValid("")).isFalse();
    }

    @Test
    @DisplayName("should throw JwtException when parsing invalid token directly")
    void shouldThrowOnDirectParse() {
        assertThatThrownBy(() -> jwtService.validateAndParseClaims("invalid.token.here"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("should return configured expiration ms")
    void shouldReturnExpirationMs() {
        assertThat(jwtService.getExpirationMs()).isEqualTo(EXPIRY_MS);
    }
}
