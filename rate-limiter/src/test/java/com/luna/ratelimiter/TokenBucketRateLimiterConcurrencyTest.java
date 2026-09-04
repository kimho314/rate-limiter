package com.luna.ratelimiter;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.luna.ratelimiter.ratelimit.RateLimiter;
import com.luna.ratelimiter.tier.Tier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
public class TokenBucketRateLimiterConcurrencyTest {

    @Container
    static final GenericContainer<?> redis =
        new GenericContainer<>(DockerImageName.parse("redis:8.10-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    RateLimiter limiter;
    @Autowired
    StringRedisTemplate redisTemplate;

    @BeforeEach
    void clean() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void concurrentRequestsAgainstSingleBucket_exactlyCapacityAllowed() throws Exception {
        // PREMIUM has capacity 100 in test config (and prod)
        final Tier tier = Tier.PREMIUM;
        final int capacity = 100;
        final int totalRequests = 500;
        final int threads = 32;

        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger denied = new AtomicInteger();

        boolean finished;
        try (ExecutorService pool = Executors.newFixedThreadPool(threads);) {
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(totalRequests);

            for (int i = 0; i < totalRequests; i++) {
                pool.submit(() -> {
                    try {
                        start.await(); // align all threads to a starting line
                        var result = limiter.tryConsume("test:concurrent", tier);
                        if (result.allowed()) {
                            allowed.incrementAndGet();
                        } else {
                            denied.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            finished = done.await(30, TimeUnit.SECONDS);
            pool.shutdownNow();
        }

        assertThat(finished).as("all requests must complete in time").isTrue();
        assertThat(allowed.get() + denied.get()).isEqualTo(totalRequests);

        // Wall clock for the whole burst is short enough that refill is ~0,
        // so the bucket should grant essentially capacity and reject the rest.
        assertThat(allowed.get())
            .as("allowed count must not exceed capacity by more than refill slack")
            .isBetween(capacity, capacity + 5);

        assertThat(denied.get()).isEqualTo(totalRequests - allowed.get());
    }
}
