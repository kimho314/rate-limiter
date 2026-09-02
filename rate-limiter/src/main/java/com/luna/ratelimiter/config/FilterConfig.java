package com.luna.ratelimiter.config;

import com.luna.ratelimiter.ratelimit.RateLimitFilter;
import com.luna.ratelimiter.ratelimit.RateLimiter;
import com.luna.ratelimiter.tier.TierResolver;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class FilterConfig {

    @Bean
    RateLimitFilter rateLimitFilter(TierResolver tierResolver, RateLimiter rateLimiter) {
        return new RateLimitFilter(tierResolver, rateLimiter);
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        reg.addUrlPatterns("/api/*");
        return reg;
    }
}
