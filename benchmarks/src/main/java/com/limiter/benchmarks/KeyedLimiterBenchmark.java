package com.limiter.benchmarks;

import com.limiter.core.KeyedTokenBucketLimiter;
import com.limiter.core.KeyedTokenBucketLimiter.Result;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.ThreadParams;

import java.util.concurrent.TimeUnit;

@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class KeyedLimiterBenchmark {
    private static final long CAPACITY = 1_000_000_000L;
    private static final double REFILL_PER_SECOND = 1_000_000_000d;
    private static final int MAX_KEYS = 1_024;

    @State(Scope.Benchmark)
    public static class Hot {
        KeyedTokenBucketLimiter limiter;

        @Setup(Level.Trial)
        public void setup() {
            limiter = new KeyedTokenBucketLimiter(CAPACITY, REFILL_PER_SECOND, MAX_KEYS, Long.MAX_VALUE);
        }
    }

    @State(Scope.Benchmark)
    public static class Spread {
        KeyedTokenBucketLimiter limiter;
        String[] keys;

        @Setup(Level.Trial)
        public void setup() {
            limiter = new KeyedTokenBucketLimiter(CAPACITY, REFILL_PER_SECOND, MAX_KEYS, Long.MAX_VALUE);
            keys = new String[64];
            for (int i = 0; i < keys.length; i++) {
                keys[i] = "user-" + i;
                limiter.tryConsume(keys[i], 1);
            }
        }
    }

    @State(Scope.Benchmark)
    public static class Full {
        KeyedTokenBucketLimiter limiter;

        @Setup(Level.Trial)
        public void setup() {
            limiter = new KeyedTokenBucketLimiter(CAPACITY, REFILL_PER_SECOND, MAX_KEYS, Long.MAX_VALUE);
            for (int i = 0; i < MAX_KEYS; i++) {
                limiter.tryConsume("resident-" + i, 1);
            }
        }
    }

    @Benchmark
    public Result hotKey(Hot state) {
        return state.limiter.tryConsume("shared", 1);
    }

    @Benchmark
    public Result spreadKeys(Spread state, ThreadParams thread) {
        return state.limiter.tryConsume(state.keys[thread.getThreadIndex() % state.keys.length], 1);
    }

    @Benchmark
    public Result fullRegistryUnseenKey(Full state) {
        return state.limiter.tryConsume("unseen", 1);
    }
}
