package com.limiter.core;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Bounded, process-local registry of token buckets. Registry operations are serialized so
 * eviction cannot replace a bucket while a request is consuming from it.
 *
 * <p>Entries are kept in access order. Idle eviction walks from the least-recently used
 * entry and stops at the first entry that is still inside the idle window. While the clock
 * is monotonic, that prefix is exactly the idle set, so a request does amortized O(1) work
 * beyond the entries it actually removes.
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
    private final Map<String, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);

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

    /**
     * Consumes tokens for {@code key}, evicting idle entries first.
     *
     * @throws NullPointerException if {@code key} is null
     * @throws IllegalArgumentException if {@code key} is blank, {@code tokensRequested} is not
     *         positive, or {@code tokensRequested} exceeds capacity
     */
    public synchronized Result tryConsume(String key, long tokensRequested) {
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) throw new IllegalArgumentException("key must not be blank");
        if (tokensRequested <= 0) throw new IllegalArgumentException("tokensRequested must be positive");
        if (tokensRequested > capacity) {
            throw new IllegalArgumentException("tokensRequested exceeds capacity");
        }

        long now = nanoClock.getAsLong();
        evictIdle(now);
        // get moves a resident key to the most-recent end; lastAttempt is updated to match.
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

    /** Resident entries, including entries that are idle until the next valid request. */
    public synchronized int size() {
        return entries.size();
    }

    private void evictIdle(long now) {
        Iterator<Entry> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            long elapsed = now - iterator.next().lastAttempt;
            // Same predicate as a full scan. Access order matches last-attempt order when the
            // clock is monotonic, so the first miss ends the walk.
            if (elapsed >= idleNanos && elapsed >= 0) {
                iterator.remove();
            } else {
                break;
            }
        }
    }
}
