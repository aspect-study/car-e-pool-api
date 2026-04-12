package com.carpool.service.auth;

import com.carpool.common.exception.InvalidTelegramAuthException;
import com.carpool.domain.entity.User;
import com.carpool.domain.enums.UserStatus;
import com.carpool.repository.UserRepository;
import com.carpool.service.dto.request.TelegramAuthRequest;
import com.carpool.service.dto.response.AuthResponse;
import com.carpool.service.mapper.EntityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.TreeMap;

/**
 * Handles Telegram Login Widget authentication.
 *
 * Verification algorithm (per Telegram docs):
 *   1. Build data_check_string from all fields except 'hash', sorted alphabetically
 *   2. secret_key = SHA256(bot_token)  ← NOT HMAC, raw SHA256
 *   3. expected_hash = HMAC-SHA256(data_check_string, secret_key)
 *   4. Compare expected_hash with provided hash (constant-time comparison)
 *   5. Verify auth_date is not older than maxAgeSeconds
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramAuthService {

    private final UserRepository userRepository;
    private final JwtService     jwtService;
    private final EntityMapper   mapper;

    @Value("${carpool.telegram.bot-token}")
    private String botToken;

    @Value("${carpool.telegram.auth-max-age-seconds}")
    private long authMaxAgeSeconds;

    @Transactional
    public AuthResponse authenticate(TelegramAuthRequest request) {
        verifyTelegramHash(request);
        verifyAuthDateFreshness(request.authDate());

        User user = userRepository.findByTelegramId(request.id())
                .map(existing -> updateUserProfile(existing, request))
                .orElseGet(() -> createNewUser(request));

        // Suspended/banned users cannot log in
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Login attempt from non-active user telegramId={} status={}",
                    request.id(), user.getStatus());
            throw new InvalidTelegramAuthException();
        }

        String token = jwtService.generateToken(
                user.getId(),
                user.getTelegramId(),
                user.getRole().name()
        );

        log.info("User authenticated telegramId={} userId={} role={}",
                user.getTelegramId(), user.getId(), user.getRole());

        return new AuthResponse(
                token,
                "Bearer",
                jwtService.getExpirationMs(),
                mapper.toUserResponse(user)
        );
    }

    /**
     * Verifies the HMAC-SHA256 hash signature from Telegram.
     * Uses constant-time comparison to prevent timing attacks.
     */
    private void verifyTelegramHash(TelegramAuthRequest request) {
        try {
            // Step 1: Build sorted data_check_string (all fields except 'hash')
            var fields = new TreeMap<String, String>();
            fields.put("id", String.valueOf(request.id()));
            fields.put("first_name", request.firstName());
            fields.put("auth_date", String.valueOf(request.authDate()));

            if (request.lastName()  != null) fields.put("last_name",  request.lastName());
            if (request.username()  != null) fields.put("username",   request.username());
            if (request.photoUrl()  != null) fields.put("photo_url",  request.photoUrl());

            // "id=123\nfirst_name=John\nauth_date=..." (newline-separated, sorted)
            String dataCheckString = fields.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");

            // Step 2: secret_key = SHA256(bot_token)
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] secretKey = sha256.digest(botToken.getBytes(StandardCharsets.UTF_8));

            // Step 3: HMAC-SHA256(data_check_string, secret_key)
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
            byte[] expectedHashBytes = hmac.doFinal(
                    dataCheckString.getBytes(StandardCharsets.UTF_8));
            String expectedHash = HexFormat.of().formatHex(expectedHashBytes);

            // Step 4: Constant-time comparison — prevents timing side-channel attack
            if (!MessageDigest.isEqual(
                    expectedHash.getBytes(StandardCharsets.UTF_8),
                    request.hash().getBytes(StandardCharsets.UTF_8))) {
                log.warn("Telegram hash verification failed for id={}", request.id());
                throw new InvalidTelegramAuthException();
            }

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // These should never happen with standard JDK
            throw new RuntimeException("Crypto algorithm unavailable", e);
        }
    }

    /**
     * Rejects auth data older than authMaxAgeSeconds.
     * Prevents replay attacks using captured auth payloads.
     */
    private void verifyAuthDateFreshness(long authDate) {
        long ageSeconds = Instant.now().getEpochSecond() - authDate;
        if (ageSeconds > authMaxAgeSeconds) {
            log.warn("Telegram auth data expired: ageSeconds={}", ageSeconds);
            throw new InvalidTelegramAuthException();
        }
    }

    private User createNewUser(TelegramAuthRequest request) {
        String fullName = request.lastName() != null
                ? request.firstName() + " " + request.lastName()
                : request.firstName();

        User user = User.builder()
                .telegramId(request.id())
                .telegramHandle(request.username())
                .fullName(fullName)
                .photoUrl(request.photoUrl())
                .build();

        log.info("Auto-creating new user telegramId={} name={}", request.id(), fullName);
        return userRepository.save(user);
    }

    /**
     * Refresh mutable profile fields that may change on the Telegram side
     * (user can update their name or photo in Telegram settings).
     */
    private User updateUserProfile(User user, TelegramAuthRequest request) {
        String fullName = request.lastName() != null
                ? request.firstName() + " " + request.lastName()
                : request.firstName();

        user.setFullName(fullName);
        user.setTelegramHandle(request.username());
        user.setPhotoUrl(request.photoUrl());
        return userRepository.save(user);
    }

    // TEMPORARY — remove before production
    // Add this method temporarily in TelegramAuthService para may way mag-generate ng test token
    public AuthResponse authenticateTestUser(Long telegramId, String name) {
        User user = userRepository.findByTelegramId(telegramId)
                .orElseGet(() -> userRepository.save(User.builder()
                        .telegramId(telegramId)
                        .fullName(name)
                        .build()));
        String token = jwtService.generateToken(
                user.getId(), user.getTelegramId(), user.getRole().name());
        return new AuthResponse(token, "Bearer",
                jwtService.getExpirationMs(), mapper.toUserResponse(user));
    }
}
