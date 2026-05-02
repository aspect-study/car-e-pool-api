package com.carpool.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import java.util.concurrent.TimeUnit;

/**
 * Per-IP rate limiting using Bucket4j token bucket algorithm.
 *
 * Each unique IP gets its own bucket with:
 *   - capacity: max burst allowed
 *   - refill: tokens added per interval (sliding window)
 *
 * Default (from application.properties): 60 requests per 60 seconds per IP.
 *
 * Caffeine cache with TTL eviction — prevents unbounded memory growth from
 * unique IPs. Buckets expire after 1 hour of inactivity, hard cap at 100k IPs.
 * Bucket4j buckets are thread-safe individually.
 *
 * Note: For multi-instance deployments, replace with Bucket4j + Redis backend.
 * For 5k users on a single instance, in-memory is sufficient.
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .maximumSize(100_000)
            .build();
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
        Bucket bucket = buckets.get(clientIp, this::createBucket);

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
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(refillTokens, Duration.ofSeconds(refillSeconds))
                .build();
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
