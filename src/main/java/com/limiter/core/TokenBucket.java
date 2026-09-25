package com.limiter.core;

import java.util.Objects;
import java.util.function.LongSupplier;

/** Thread-safe, process-local token bucket. All time values use a monotonic nanosecond clock. */
public final class TokenBucket {
    private final long capacity;
    private final double tokensPerNanosecond;
    private final LongSupplier nanoClock;
    private double available;
    private long lastRefill;

    public TokenBucket(long capacity, double tokensPerSecond) {
        this(capacity, tokensPerSecond, System::nanoTime);
    }

    /** The clock injection allows tests to control elapsed time without sleeping. */
    public TokenBucket(long capacity, double tokensPerSecond, LongSupplier nanoClock) {
        if (capacity <= 0 || !Double.isFinite(tokensPerSecond) || tokensPerSecond <= 0) {
            throw new IllegalArgumentException("capacity and finite refill rate must be positive");
        }
        this.capacity = capacity;
        this.tokensPerNanosecond = tokensPerSecond / 1_000_000_000d;
        if (this.tokensPerNanosecond == 0) {
            throw new IllegalArgumentException("refill rate is too small");
        }
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        this.available = capacity;
        this.lastRefill = nanoClock.getAsLong();
    }

    public synchronized boolean tryConsume(long tokensRequested) {
        if (tokensRequested <= 0) {
            throw new IllegalArgumentException("tokensRequested must be positive");
        }
        refill();
        if (available < tokensRequested) return false;
        available -= tokensRequested;
        return true;
    }

    private void refill() {
        long now = nanoClock.getAsLong();
        // nanoTime wraps after centuries; signed subtraction still works for intervals < 2^63 ns.
        long elapsed = now - lastRefill;
        if (elapsed <= 0) return;
        available = Math.min(capacity, available + elapsed * tokensPerNanosecond);
        lastRefill = now;
    }
}
