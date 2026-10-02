package com.limiter.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Bounded, process-local registry of token buckets. Registry operations are serialized so
 * eviction cannot replace a bucket while a request is consuming from it.
 */
public final class KeyedTokenBucketLimiter {
    public enum Result { ALLOWED, RATE_LIMITED, REGISTRY_FULL }

    private static final class Entry {
        final TokenBucket bucket;
        long lastAttempt;

        Entry(TokenBucket bucket, long lastAttempt) {
            this.bucket = bucket;
            this.lastAttempt = lastAttempt;
        }
    }

    private final long capacity;
    private final double tokensPerSecond;
    private final int maxKeys;
    private final long idleNanos;
    private final LongSupplier nanoClock;
    private final Map<String, Entry> entries = new HashMap<>();

    public KeyedTokenBucketLimiter(long capacity, double tokensPerSecond, int maxKeys, long idleNanos) {
        this(capacity, tokensPerSecond, maxKeys, idleNanos, System::nanoTime);
    }

    public KeyedTokenBucketLimiter(long capacity, double tokensPerSecond, int maxKeys,
                                   long idleNanos, LongSupplier nanoClock) {
        if (maxKeys <= 0 || idleNanos <= 0) {
            throw new IllegalArgumentException("maxKeys and idleNanos must be positive");
        }
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        // Reuse the bucket's capacity and refill-rate validation.
        new TokenBucket(capacity, tokensPerSecond, nanoClock);
        this.capacity = capacity;
        this.tokensPerSecond = tokensPerSecond;
        this.maxKeys = maxKeys;
        this.idleNanos = idleNanos;
    }

    public synchronized Result tryConsume(String key, long tokensRequested) {
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) throw new IllegalArgumentException("key must not be blank");
        if (tokensRequested <= 0) throw new IllegalArgumentException("tokensRequested must be positive");

        long now = nanoClock.getAsLong();
        evictIdle(now);
        Entry entry = entries.get(key);
        if (entry == null) {
            if (entries.size() >= maxKeys) return Result.REGISTRY_FULL;
            entry = new Entry(new TokenBucket(capacity, tokensPerSecond, nanoClock), now);
            entries.put(key, entry);
        }
        // Both successful and denied attempts count as activity.
        entry.lastAttempt = now;
        return entry.bucket.tryConsume(tokensRequested) ? Result.ALLOWED : Result.RATE_LIMITED;
    }

    /** Resident entries, including entries that have become idle but await the next new key. */
    public synchronized int size() {
        return entries.size();
    }

    private void evictIdle(long now) {
        Iterator<Entry> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            long elapsed = now - iterator.next().lastAttempt;
            if (elapsed >= idleNanos && elapsed >= 0) iterator.remove();
        }
    }
}
