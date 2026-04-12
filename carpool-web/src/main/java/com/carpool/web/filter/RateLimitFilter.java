package com.carpool.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP rate limiting using Bucket4j token bucket algorithm.
 *
 * Each unique IP gets its own bucket with:
 *   - capacity: max burst allowed
 *   - refill: tokens added per interval (sliding window)
 *
 * Default (from application.properties): 60 requests per 60 seconds per IP.
 *
 * ConcurrentHashMap is safe here — Bucket4j buckets are thread-safe individually,
 * and computeIfAbsent is atomic for bucket creation.
 *
 * Note: For multi-instance deployments, replace with Bucket4j + Redis backend.
 * For 5k users on a single instance, in-memory is sufficient.
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final int capacity;
    private final int refillTokens;
    private final int refillSeconds;

    public RateLimitFilter(
            @Value("${carpool.rate-limit.capacity}") int capacity,
            @Value("${carpool.rate-limit.refill-tokens}") int refillTokens,
            @Value("${carpool.rate-limit.refill-seconds}") int refillSeconds) {
        this.capacity      = capacity;
        this.refillTokens  = refillTokens;
        this.refillSeconds = refillSeconds;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String clientIp = resolveClientIp(request);
        Bucket bucket   = buckets.computeIfAbsent(clientIp, this::createBucket);

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for IP: {}", clientIp);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                    "success", false,
                    "error", Map.of(
                            "code",    "RATE_LIMIT_EXCEEDED",
                            "message", "Too many requests. Please slow down."
                    )
            )));
        }
    }

    private Bucket createBucket(String ip) {
        Bandwidth limit = Bandwidth.classic(capacity,
                Refill.greedy(refillTokens, Duration.ofSeconds(refillSeconds)));
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Respects X-Forwarded-For header for clients behind a reverse proxy/nginx.
     * Falls back to direct remote address if header is absent.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // Header may contain comma-separated chain: "client, proxy1, proxy2"
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
