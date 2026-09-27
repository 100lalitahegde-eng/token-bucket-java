# Token bucket rate limiter (Java)

A thread-safe, in-process rate limiter built around a token bucket. Java 17, Maven and JUnit 5.

## Run

```bash
mvn test
```

Use `new TokenBucket(50, 10).tryConsume(1)` to allow a burst of 50 and refill at 10 tokens per second. Calls consume immediately or return false; there is no queue or retry.

For independent callers in one JVM, use a bounded keyed registry:

```java
KeyedTokenBucketLimiter limiter = new KeyedTokenBucketLimiter(
    50, 10, 1_000, 60_000_000_000L); // burst, tokens/sec, max keys, 60s idle
KeyedTokenBucketLimiter.Result result = limiter.tryConsume("user-123", 1);
```

`ALLOWED` consumes tokens; `RATE_LIMITED` means this key lacks tokens; `REGISTRY_FULL` means a new key could not be admitted because all resident entries are active. Callers must handle `REGISTRY_FULL` explicitly; it is not an allow decision. Keys must be non-null and nonblank.

## Architecture

One bucket owns capacity, current balance and last refill time. Its synchronized operation refills to the capacity ceiling, then decides and deducts atomically. An injected monotonic nanosecond clock makes refill tests deterministic. Invalid capacities, rates and requests fail fast.

The registry creates one bucket per key, up to `maxKeys`. Every attempt for a resident key, including a rate-limited attempt, refreshes its idle time. On each valid request it removes entries idle for at least `idleNanos`; eviction discards the old balance, so a returning key receives a fresh burst. If all `maxKeys` entries are still active, a new key receives `REGISTRY_FULL` without displacing them. `size()` reports resident entries, which may include idle entries until the next request. Registry lookup, cleanup, creation and consumption share one lock, favouring simple atomic behaviour over hot-key throughput.

| Choice | Reason | Limitation |
| --- | --- | --- |
| `synchronized` per bucket and registry | Atomic updates and no eviction while a call is consuming | Registry-wide contention |
| `System.nanoTime` | Elapsed time is independent of wall-clock corrections | Clock is meaningful only within one process |
| `double` balance | Fractional refill without timer threads | Floating-point rounding near a decision boundary |
| No distributed adapter | Avoid claiming a cluster guarantee from local state | Each process enforces its own independent limit |

**Scope:** one JVM. The keyed registry provides local key isolation, not cross-process fairness, durability, HTTP enforcement or distributed enforcement. A deployment with multiple replicas grants each replica its own buckets. The original exploratory snippets remain at the repository root for provenance; the runnable implementation lives under `src/`.

## Evidence

- `TokenBucketTest` checks burst exhaustion, partial refill, capacity ceiling, invalid input and 100 concurrent attempts with a frozen clock. Exactly 50 must succeed.
- `KeyedTokenBucketLimiterTest` checks key isolation, registry exhaustion, exact idle eviction, activity after denial, invalid input and concurrent first use of one key.
- GitHub Actions runs the tests and compiles the standalone JMH benchmark module on pushes and pull requests. It does not run performance measurements.

## Next design exercise

Run the [benchmark harness](benchmarks/README.md) on a documented machine to collect hot-key, spread-key and full-registry throughput and sampled p95/p99 latency. Only then decide whether registry contention warrants a design change. If cluster-wide enforcement becomes necessary, use an atomic server-side operation and specify behaviour when storage is unavailable.

## Interview walkthrough

Start with the invariant: the balance stays between zero and capacity and one consume operation cannot overspend it. Explain why wall-clock changes or a non-atomic read/decrement can break it. For the registry, explain bounded memory, idle reset of burst allowance, and the explicit `REGISTRY_FULL` response. Discuss when a local limiter is appropriate and when distributed state is required.
