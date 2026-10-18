package com.limiter.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.limiter.core.KeyedTokenBucketLimiter.Result.*;
import static org.junit.jupiter.api.Assertions.*;

class KeyedTokenBucketLimiterTest {
    @Test void isolatesKeysAndRefusesNewKeyAtCapacity() {
        AtomicLong clock = new AtomicLong();
        KeyedTokenBucketLimiter limiter = new KeyedTokenBucketLimiter(2, 1, 2, 1_000_000_000L, clock::get);
        assertEquals(ALLOWED, limiter.tryConsume("alice", 2));
        assertEquals(RATE_LIMITED, limiter.tryConsume("alice", 1));
        assertEquals(ALLOWED, limiter.tryConsume("bob", 2));
        assertEquals(REGISTRY_FULL, limiter.tryConsume("charlie", 1));
        assertEquals(2, limiter.size());
        assertEquals(RATE_LIMITED, limiter.tryConsume("bob", 1));
    }

    @Test void evictsAtExactIdleBoundaryAndResetsReturningKey() {
        AtomicLong clock = new AtomicLong();
        KeyedTokenBucketLimiter limiter = new KeyedTokenBucketLimiter(1, 1, 1, 100, clock::get);
        assertEquals(ALLOWED, limiter.tryConsume("alice", 1));
        clock.set(99);
        assertEquals(REGISTRY_FULL, limiter.tryConsume("bob", 1));
        clock.set(100);
        assertEquals(ALLOWED, limiter.tryConsume("bob", 1));
        assertEquals(REGISTRY_FULL, limiter.tryConsume("alice", 1));
        clock.set(200);
        assertEquals(ALLOWED, limiter.tryConsume("alice", 1));
        assertEquals(1, limiter.size());
    }

    @Test void deniedAttemptRefreshesIdleTime() {
        AtomicLong clock = new AtomicLong();
        KeyedTokenBucketLimiter limiter = new KeyedTokenBucketLimiter(1, 1, 1, 100, clock::get);
        assertEquals(ALLOWED, limiter.tryConsume("alice", 1));
        clock.set(90);
        assertEquals(RATE_LIMITED, limiter.tryConsume("alice", 1));
        clock.set(100);
        assertEquals(REGISTRY_FULL, limiter.tryConsume("bob", 1));
        clock.set(190);
        assertEquals(ALLOWED, limiter.tryConsume("bob", 1));
    }

    @Test void validatesInputsBeforeChangingRegistry() {
        AtomicLong clock = new AtomicLong();
        assertThrows(IllegalArgumentException.class, () -> new KeyedTokenBucketLimiter(0, 1, 1, 1, clock::get));
        assertThrows(IllegalArgumentException.class, () -> new KeyedTokenBucketLimiter(1, 0, 1, 1, clock::get));
        assertThrows(IllegalArgumentException.class, () -> new KeyedTokenBucketLimiter(1, 1, 0, 1, clock::get));
        assertThrows(IllegalArgumentException.class, () -> new KeyedTokenBucketLimiter(1, 1, 1, 0, clock::get));
        assertThrows(NullPointerException.class, () -> new KeyedTokenBucketLimiter(1, 1, 1, 1, null));
        KeyedTokenBucketLimiter limiter = new KeyedTokenBucketLimiter(1, 1, 1, 1, clock::get);
        assertThrows(NullPointerException.class, () -> limiter.tryConsume(null, 1));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryConsume("  ", 1));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryConsume("alice", 0));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryConsume("alice", 2));
        assertEquals(0, limiter.size());
    }

    @Test void requestAboveCapacityDoesNotAdmitOrRefresh() {
        AtomicLong clock = new AtomicLong();
        KeyedTokenBucketLimiter limiter = new KeyedTokenBucketLimiter(1, 1, 1, 100, clock::get);
        assertEquals(ALLOWED, limiter.tryConsume("alice", 1));
        clock.set(90);
        assertThrows(IllegalArgumentException.class, () -> limiter.tryConsume("alice", 2));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryConsume("bob", 2));
        assertEquals(1, limiter.size());
        clock.set(100);
        assertEquals(ALLOWED, limiter.tryConsume("bob", 1));
        assertEquals(1, limiter.size());
    }

    @Test void evictsIdlePrefixInAccessOrderAndKeepsNewerKeys() {
        AtomicLong clock = new AtomicLong();
        KeyedTokenBucketLimiter limiter = new KeyedTokenBucketLimiter(1, 1, 8, 100, clock::get);
        for (int i = 0; i < 8; i++) {
            clock.set(i);
            assertEquals(ALLOWED, limiter.tryConsume("k" + i, 1));
        }
        clock.set(50);
        assertEquals(RATE_LIMITED, limiter.tryConsume("k5", 1));
        assertEquals(RATE_LIMITED, limiter.tryConsume("k6", 1));
        assertEquals(RATE_LIMITED, limiter.tryConsume("k7", 1));
        clock.set(104);
        // k0..k4 were last touched at t = 0..4, so each is idle. k5..k7 were touched at t = 50.
        assertEquals(ALLOWED, limiter.tryConsume("fresh", 1));
        assertEquals(4, limiter.size());
        assertEquals(RATE_LIMITED, limiter.tryConsume("k7", 1));
        assertEquals(ALLOWED, limiter.tryConsume("k0", 1));
        assertEquals(ALLOWED, limiter.tryConsume("k4", 1));
    }

    @Test void simultaneousFirstRequestsShareOneBucket() throws Exception {
        int workers = 40;
        AtomicLong clock = new AtomicLong();
        KeyedTokenBucketLimiter limiter = new KeyedTokenBucketLimiter(10, 1, 1, 1_000_000_000L, clock::get);
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        try {
            Future<?>[] futures = new Future<?>[workers];
            for (int i = 0; i < workers; i++) {
                futures[i] = pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    if (limiter.tryConsume("same-key", 1) == ALLOWED) accepted.incrementAndGet();
                    return null;
                });
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> future : futures) future.get(10, TimeUnit.SECONDS);
            assertEquals(10, accepted.get());
            assertEquals(1, limiter.size());
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }
}
