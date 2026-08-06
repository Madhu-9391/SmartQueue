package com.smartqueue.config;

import io.github.bucket4j.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BUG 11 FIX: Per-IP rate limiting using Bucket4j (token bucket algorithm).
 *
 * Limits:
 *   - /api/appointments/book  → 10 req/min (prevents booking spam)
 *   - /api/auth/register      → 5 req/min  (prevents account spam)
 *   - /api/auth/login         → 20 req/min (brute-force protection)
 *   - All other endpoints     → 200 req/min (general limit)
 */
@Component
@Slf4j
public class RateLimitConfig extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${app.rate.limit.booking:10}")   private int bookingLimit;
    @Value("${app.rate.limit.register:5}")   private int registerLimit;
    @Value("${app.rate.limit.login:20}")     private int loginLimit;
    @Value("${app.rate.limit.general:200}")  private int generalLimit;

    private Bucket resolveBucket(String ip, String path) {
        String key = ip + ":" + resolveLimitKey(path);
        return buckets.computeIfAbsent(key, k -> createBucket(resolveLimit(path)));
    }

    private String resolveLimitKey(String path) {
        if (path.contains("/appointments/book")) return "book";
        if (path.contains("/auth/register"))     return "register";
        if (path.contains("/auth/login"))        return "login";
        return "general";
    }

    private int resolveLimit(String path) {
        if (path.contains("/appointments/book")) return bookingLimit;
        if (path.contains("/auth/register"))     return registerLimit;
        if (path.contains("/auth/login"))        return loginLimit;
        return generalLimit;
    }

    private Bucket createBucket(int requestsPerMinute) {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(requestsPerMinute,
                        Refill.intervally(requestsPerMinute, Duration.ofMinutes(1))))
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String ip   = resolveClientIp(request);
        String path = request.getRequestURI();

        // Skip rate limiting for static assets, swagger, h2-console, actuator
        if (shouldSkip(path)) {
            chain.doFilter(request, response);
            return;
        }

        Bucket bucket = resolveBucket(ip, path);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
        } else {
            long waitSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
            response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitSeconds));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"success\":false,\"message\":\"Too many requests. Please wait " + waitSeconds + " seconds.\"}");
            log.warn("Rate limit exceeded: ip={} path={}", ip, path);
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank())
            return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    private boolean shouldSkip(String path) {
        return path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs")
                || path.startsWith("/h2-console") || path.startsWith("/actuator")
                || path.startsWith("/ws");
    }
}
