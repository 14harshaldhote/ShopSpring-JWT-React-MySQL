package com.shopeefy.security;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.shopeefy.config.AppProperties;
import com.shopeefy.config.AppProperties.BucketSpec;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

/**
 * Token-bucket rate limiting (Bucket4j).                                  [OWASP A01:2025, A06:2025, A07:2025]
 * <p>
 * Each (policy, key) pair owns a bucket holding {@code capacity} tokens. A request takes one token;
 * tokens flow back at {@code refillTokens / refillPeriod} ("greedy" refill spreads them evenly, so
 * a client that waits a little gets a little back). An empty bucket means HTTP 429 plus the exact
 * number of seconds until the next token, sent as {@code Retry-After}.
 * <p>
 * Buckets live in a size-bounded Caffeine cache, so a flood of random keys can't exhaust memory
 * (an attacker only evicts idle buckets, never the ones that are actively throttling them).
 * For several instances, swap the cache for Bucket4j's Redis/JDBC proxy manager; the API stays the same.
 */
@Component
public class RateLimiter {

    private final Map<String, BucketSpec> specs;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(200_000)
            .expireAfterAccess(Duration.ofHours(2))
            .build();

    public RateLimiter(AppProperties props) {
        this.specs = props.security().rateLimits();
    }

    public Decision tryConsume(String policy, String key) {
        BucketSpec spec = specs.get(policy);
        if (spec == null) {
            throw new IllegalArgumentException("Unknown rate-limit policy " + policy);
        }
        Bucket bucket = buckets.get(policy + ':' + key, k -> Bucket.builder()
                .addLimit(limit -> limit.capacity(spec.capacity()).refillGreedy(spec.refillTokens(), spec.refillPeriod()))
                .build());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        long retryAfter = probe.isConsumed() ? 0
                : Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill() + 999_999_999L));
        return new Decision(probe.isConsumed(), probe.getRemainingTokens(), spec.capacity(), retryAfter);
    }

    /** Test hook: forget every bucket. */
    public void reset() {
        buckets.invalidateAll();
    }

    public record Decision(boolean allowed, long remaining, long limit, long retryAfterSeconds) {
    }
}
