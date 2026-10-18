# Token bucket rate limiter

Thread-safe token bucket for Java 17, with a bounded keyed limiter and idle eviction.

`TokenBucket` holds a balance up to a fixed capacity and refills it from an injected monotonic nanosecond clock. `tryConsume` takes the requested tokens immediately or returns false. Requests are not queued.

`KeyedTokenBucketLimiter` stores one bucket per key, up to `maxKeys`, and drops entries that have been idle for `idleNanos`. A call returns:

| Result | Meaning |
| --- | --- |
| `ALLOWED` | The requested tokens were consumed. |
| `RATE_LIMITED` | This key does not currently have enough tokens. |
| `REGISTRY_FULL` | The key is not resident, and every resident entry is still active. |

`REGISTRY_FULL` does not consume tokens. The caller decides how to respond.

```java
TokenBucket bucket = new TokenBucket(50, 10);
bucket.tryConsume(1);

KeyedTokenBucketLimiter limiter = new KeyedTokenBucketLimiter(
    50, 10, 1_000, 60_000_000_000L); // capacity, tokens per second, max keys, 60s idle
KeyedTokenBucketLimiter.Result result = limiter.tryConsume("user-123", 1);
```

Keys are non-null and non-blank. `tryConsume` throws `IllegalArgumentException` when the requested count is not positive or is greater than capacity. A count above capacity can never succeed.

## Design

A bucket stores its capacity, balance, and last refill time. One `synchronized` method refills up to the capacity ceiling, then accepts or rejects the request and deducts in the same critical section. The clock is a `LongSupplier`, so tests advance time directly. The balance is a `double`, which lets a fractional refill accumulate between calls. `System.nanoTime` moves independently of wall-clock adjustments and is meaningful within one process.

The registry keeps entries in an access-ordered `LinkedHashMap`. Every valid request for a resident key, including a denial, moves that key to the most-recent end and refreshes its idle time. Before the lookup, eviction walks from the least-recently used entry and removes entries whose idle time is at least `idleNanos`, stopping at the first entry that is still active. With a monotonic clock that prefix is the full idle set, so the walk is amortized O(1) per request. A removed key drops its balance, and a later request for it starts a new burst. If `maxKeys` entries remain after eviction, a new key receives `REGISTRY_FULL` and no resident key is displaced. `size()` reports resident entries, including keys that are idle until the next valid request. Invalid arguments are rejected before the registry changes. Lookup, eviction, insertion, and consumption share one lock.

| Choice | Reason | Limit |
| --- | --- | --- |
| `synchronized` on the bucket and the registry | Refill, deduction, and eviction stay atomic | Every key shares one registry lock |
| Access-ordered map | Idle eviction stops after the idle prefix | One request still removes each expired entry |
| `System.nanoTime` | Elapsed time ignores wall-clock steps | Readings compare only within one process |
| `double` balance | Fractional refill without a timer thread | The comparison uses a binary floating-point balance |

## Build and test

```bash
mvn test
```

The build targets Java 17. GitHub Actions runs the tests and compiles the JMH module on every push and pull request. Performance runs are a separate, manual workflow.

`TokenBucketTest` covers burst exhaustion, partial refill, the capacity ceiling, invalid input (including a request above capacity), and 100 concurrent attempts against a frozen clock. Exactly 50 succeed. `KeyedTokenBucketLimiterTest` covers key isolation, a full registry, eviction at the idle boundary, access-order eviction of an idle prefix, idle refresh on a denied attempt, rejection of a request above capacity without admitting or refreshing a key, and concurrent first use of one key.

## Benchmarks

Harness details and recorded results are in [benchmarks/README.md](benchmarks/README.md) and [PERFORMANCE.md](PERFORMANCE.md).

On a GitHub-hosted runner (Linux x86-64, 2 CPUs, Temurin 17, JMH 1.37, two forks), one-thread throughput was 11.50 million ops/s for a hot key, 3.86 million ops/s for spread keys, and 0.300 million ops/s for an absent key against a full registry of 1,024 entries. That revision scanned every resident entry on each call. Sampled one-thread p99 was 0.140 µs, 0.501 µs, and 6.552 µs for those three workloads. The same host at 4 and 16 threads is in `PERFORMANCE.md`. Both counts exceed its two visible CPUs.

The same full-registry workload was measured again on 18 October 2026, on one 4-vCPU Linux VM, immediately before and after idle eviction stopped at the first active entry. At one thread, throughput moved from 348,400 ± 6,355 ops/s to 20,674,704 ± 583,730 ops/s. Two-thread results and percentiles are in `PERFORMANCE.md`.

## Limitations

Enforcement is per JVM. Each process, and each replica, keeps its own buckets. The registry does not share state across processes and does not provide durability or an HTTP integration.
