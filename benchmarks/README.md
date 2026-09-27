# Keyed registry performance baseline

The standalone JMH module depends on the application JAR. From the repository root, run:

```bash
bash benchmarks/run.sh
```

This runs three workloads at 1, 4 and 16 threads, in separate throughput and sampled-latency runs. JMH uses three one-second warm-up iterations, five one-second measurement iterations, and two forks. JSON output and environment details are written under `benchmarks/results/` (ignored by Git). The script records the Java version, OS and CPU details; record CPU governor, container limits and background load separately when relevant. Keep the full JSON beside any reported summary.

| Workload | Shared registry state | What it exercises |
| --- | --- | --- |
| `hotKey` | One active key | Contention on one key and the registry lock |
| `spreadKeys` | 64 pre-created keys, assigned by worker index | Multiple keys still contending on the registry lock |
| `fullRegistryUnseenKey` | 1,024 resident keys; fixed absent key | Explicit `REGISTRY_FULL` path and idle-scan cost |

Capacity and refill are high so the first two workloads predominantly exercise the allow path; confirm the result distribution for any changed parameters. `idleNanos = Long.MAX_VALUE` prevents normal eviction during a run. The full-registry workload repeatedly uses one absent key; it does not measure allocation of unique keys or steady-state eviction. Every measured call includes registry lookup and clock reads; the spread workload also includes array indexing.

For each workload and thread count, report throughput (operations/second) and sampled p95/p99 latency (microseconds), along with the environment and raw JSON. These are two separate JMH runs; do not treat them as simultaneous throughput and latency measurements. JMH sampling estimates a distribution rather than timing every call. Compare runs on the same machine and configuration, and rerun before interpreting small differences. CI compiles the benchmark but does not publish performance numbers or impose thresholds.

These measurements describe this JVM and workload, not production service latency or cluster-wide enforcement. The registry remains process-local and serializes requests across keys.
