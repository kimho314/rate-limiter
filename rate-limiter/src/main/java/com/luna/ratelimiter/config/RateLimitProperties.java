package com.luna.ratelimiter.config;

import com.luna.ratelimiter.tier.Tier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ratelimit")
public class RateLimitProperties {
    @NotNull
    private Map<Tier, @Valid TierLimit> tiers = new EnumMap<>(Tier.class);

    @NotNull
    private FailureMode failureMode = FailureMode.ALLOW;

    public Map<Tier, TierLimit> getTiers() {
        return tiers;
    }

    public FailureMode getFailureMode() {
        return failureMode;
    }

    public void setTiers(
        Map<Tier, TierLimit> tiers) {
        this.tiers = tiers;
    }

    public void setFailureMode(FailureMode failureMode) {
        this.failureMode = failureMode;
    }

    public static class TierLimit{
        // Max tokens the bucket can hold
        @Positive
        private int capacity;

        // Tokens added per second
        @Positive
        private double refillRate;

        public int getCapacity() {
            return capacity;
        }

        public double getRefillRate() {
            return refillRate;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public void setRefillRate(double refillRate) {
            this.refillRate = refillRate;
        }
    }

    public enum FailureMode{
        ALLOW, DENY
    }
}
