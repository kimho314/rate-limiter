package com.luna.ratelimiter.ratelimit;

import com.luna.ratelimiter.tier.Tier;

public interface RateLimiter {
    RateLimitResult tryConsume(String key, Tier tier);
}
