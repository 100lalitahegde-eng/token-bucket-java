# Token bucket rate limiter (Java)

A thread-safe, in-process rate limiter built around a token bucket. Java 17, Maven and JUnit 5.

## Run

```bash
mvn test
```

Use `new TokenBucket(50, 10).tryConsume(1)` to allow a burst of 50 and refill at 10 tokens per second. Calls consume immediately or return false; there is no queue or retry.

## Architecture

One bucket owns capacity, current balance and last refill time. Its synchronized operation refills to the capacity ceiling, then decides and deducts atomically. An injected monotonic nanosecond clock makes refill tests deterministic. Invalid capacities, rates and requests fail fast.

| Choice | Reason | Limitation |
| --- | --- | --- |
| `synchronized` per bucket | Simple atomic update under concurrent calls | Contention for a hot shared key |
| `System.nanoTime` | Elapsed time is independent of wall-clock corrections | Clock is meaningful only within one process |
| `double` balance | Fractional refill without timer threads | Floating-point rounding near a decision boundary |
| No distributed adapter | Avoid claiming a cluster guarantee from local state | Each process enforces its own independent limit |

**Scope:** one JVM, one bucket shared by callers. This does not provide cross-process fairness, durability, per-user isolation, HTTP enforcement or Redis integration. A keyed bucket registry would require eviction and cardinality limits. The original exploratory snippets remain at the repository root for provenance; the runnable implementation lives under `src/`.

## Evidence

- `TokenBucketTest` checks burst exhaustion, partial refill, capacity ceiling, invalid input and 100 concurrent attempts with a frozen clock. Exactly 50 must succeed.
- GitHub Actions runs `mvn test` on pushes and pull requests.

## Next design exercise

Add a keyed registry with idle eviction and benchmark throughput and tail latency under hot and spread keys. If cluster-wide enforcement becomes necessary, use an atomic server-side operation and specify behavior when storage is unavailable.

## Interview walkthrough

Start with the invariant: the balance stays between zero and capacity and one consume operation cannot overspend it. Explain why wall-clock changes or a non-atomic read/decrement can break it. Discuss when a local limiter is appropriate and when distributed state is required.
