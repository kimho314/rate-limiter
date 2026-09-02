package com.luna.ratelimiter.ratelimit;

import com.luna.ratelimiter.config.RateLimitProperties;
import com.luna.ratelimiter.config.RateLimitProperties.FailureMode;
import com.luna.ratelimiter.config.RateLimitProperties.TierLimit;
import com.luna.ratelimiter.tier.Tier;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

@Component
public class TokenBucketRateLimiter implements RateLimiter{

    private static final String KEY_PREFIX = "rl:";
    private static final Logger log = LoggerFactory.getLogger(TokenBucketRateLimiter.class);

    private final StringRedisTemplate redis;
    private final RateLimitProperties props;

    private DefaultRedisScript<List> script;

    public TokenBucketRateLimiter(StringRedisTemplate redis, RateLimitProperties props) {
        this.redis = redis;
        this.props = props;
    }

    @PostConstruct
    void loadScript() {
        DefaultRedisScript<List> s = new DefaultRedisScript<>();
        s.setScriptSource(new ResourceScriptSource(
            new ClassPathResource("scripts/token_bucket.lua")));
        s.setResultType(List.class);
        this.script = s;
        log.info("Loaded token bucket Lua script");
    }

    @Override
    public RateLimitResult tryConsume(String key, Tier tier) {
        TierLimit limit = props.getTiers().get(tier);
        if (limit == null) {
            throw new IllegalStateException("No rate limit configured for tier: " + tier);
        }

        String redisKey = KEY_PREFIX + key;
        long now = System.currentTimeMillis();

        try {
            @SuppressWarnings("unchecked")
            List<Long> result = redis.execute(
                script,
                List.of(redisKey),
                String.valueOf(limit.getCapacity()),
                String.valueOf(limit.getRefillRate()),
                String.valueOf(now),
                "1"
            );

            boolean allowed = result.get(0) == 1L;
            long remaining = result.get(1);
            long retryAfterMs = result.get(2);

            return allowed
                ? RateLimitResult.allowed(remaining, limit.getCapacity())
                : RateLimitResult.denied(remaining, limit.getCapacity(),
                    Duration.ofMillis(retryAfterMs));

        } catch (DataAccessException e) {
            return handleRedisFailure(e, limit);
        }
    }

    private RateLimitResult handleRedisFailure(DataAccessException e, TierLimit limit) {
        if (props.getFailureMode() == FailureMode.ALLOW) {
            log.warn("Redis failure, failing open: {}", e.getMessage());
            return RateLimitResult.allowed(limit.getCapacity(), limit.getCapacity());
        }
        log.warn("Redis failure, failing closed: {}", e.getMessage());
        return RateLimitResult.denied(0, limit.getCapacity(), Duration.ofSeconds(1));
    }
}
