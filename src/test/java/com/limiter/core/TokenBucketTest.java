package com.limiter.core;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class TokenBucketTest {
    @Test void burstRefillAndCap() {
        AtomicLong clock = new AtomicLong();
        TokenBucket bucket = new TokenBucket(3, 2, clock::get);
        assertTrue(bucket.tryConsume(3));
        assertFalse(bucket.tryConsume(1));
        clock.addAndGet(500_000_000L);
        assertTrue(bucket.tryConsume(1));
        assertFalse(bucket.tryConsume(1));
        clock.addAndGet(10_000_000_000L);
        assertTrue(bucket.tryConsume(3));
        assertFalse(bucket.tryConsume(1));
    }

    @Test void validatesInput() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(1, Double.NaN));
        TokenBucket bucket = new TokenBucket(1, 1);
        assertThrows(IllegalArgumentException.class, () -> bucket.tryConsume(0));
        assertThrows(IllegalArgumentException.class, () -> bucket.tryConsume(-1));
        assertFalse(bucket.tryConsume(2));
    }

    @Test void simultaneousBurstCannotExceedCapacity() throws Exception {
        final int workers = 100;
        AtomicLong clock = new AtomicLong();
        TokenBucket bucket = new TokenBucket(50, 10, clock::get);
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
                    if (bucket.tryConsume(1)) accepted.incrementAndGet();
                    return null;
                });
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> f : futures) f.get(10, TimeUnit.SECONDS);
            assertEquals(50, accepted.get());
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }
}
