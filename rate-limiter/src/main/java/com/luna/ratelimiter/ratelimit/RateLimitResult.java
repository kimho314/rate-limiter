package com.luna.ratelimiter.ratelimit;

import java.time.Duration;

public record RateLimitResult(
    boolean allowed,
    long remaining,
    long capacity,
    Duration retryAfter
) {
    public static RateLimitResult allowed(long remaining, long capacity) {
        return new RateLimitResult(true, remaining, capacity, Duration.ZERO);
    }

    public static RateLimitResult denied(long remaining, long capacity, Duration retryAfter) {
        return new RateLimitResult(false, remaining, capacity, retryAfter);
    }
}
