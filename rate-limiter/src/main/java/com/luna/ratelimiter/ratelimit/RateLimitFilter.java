package com.luna.ratelimiter.ratelimit;

import com.luna.ratelimiter.tier.Tier;
import com.luna.ratelimiter.tier.TierResolver;
import com.luna.ratelimiter.tier.TierResolver.ResolvedIdentity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public class RateLimitFilter extends OncePerRequestFilter {

    // Standard-ish rate limit headers, modeled on GitHub / Stripe
    private static final String H_LIMIT     = "X-RateLimit-Limit";
    private static final String H_REMAINING = "X-RateLimit-Remaining";
    private static final String H_RESET     = "X-RateLimit-Reset"; // seconds until refill
    private static final String H_RETRY     = "Retry-After"; // standard 429 header

    private final TierResolver tierResolver;
    private final RateLimiter rateLimiter;

    public RateLimitFilter(TierResolver tierResolver, RateLimiter rateLimiter) {
        this.tierResolver = tierResolver;
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {
        // Skip non API paths
        if (!request.getRequestURI().startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<ResolvedIdentity> maybeIdentity = tierResolver.resolve(request);
        if (maybeIdentity.isEmpty()) {
            writeUnauthorized(response);
            return;
        }
        ResolvedIdentity identity = maybeIdentity.get();
        Tier tier = identity.tier();
        String key = "user:" + identity.userId();

        RateLimitResult result = rateLimiter.tryConsume(key, tier);

        // Always advertise the limits, even on success as it helps clients self-throttle
        response.setHeader(H_LIMIT, String.valueOf(result.capacity()));
        response.setHeader(H_REMAINING, String.valueOf(result.remaining()));

        if (result.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSec = Math.max(1, result.retryAfter().toSeconds());
        response.setHeader(H_RESET, String.valueOf(retryAfterSec));
        response.setHeader(H_RETRY, String.valueOf(retryAfterSec));
        writeTooManyRequests(response, retryAfterSec);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"error":"unauthorized","message":"Missing or invalid Authorization: Bearer <jwt>"}""");
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSec) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(String.format(
            "{\"error\":\"rate_limited\",\"retry_after_seconds\":%d}", retryAfterSec));
    }
}
