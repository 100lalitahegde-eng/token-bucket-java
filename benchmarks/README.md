# Keyed registry benchmarks

The JMH module depends on the installed application JAR. From the repository root:

```bash
bash benchmarks/run.sh
```

The script runs three workloads at one thread and, when `getconf _NPROCESSORS_ONLN` reports at least two processors, at two threads. Throughput and sampled latency are separate runs. JMH uses three one-second warmup iterations, five one-second measurement iterations, and two forks. JSON and an environment record are written under `benchmarks/results/` (gitignored). The test workflow compiles this module. The benchmark workflow is manual and uploads `benchmarks/results/` as the `keyed-registry-jmh` artifact.

| Workload | Registry state | What it measures |
| --- | --- | --- |
| `hotKey` | One key | A single key under the registry lock |
| `spreadKeys` | 64 pre-created keys; each worker repeats the key selected by its thread index | Several keys sharing one registry lock |
| `fullRegistryUnseenKey` | 1,024 resident keys and one fixed absent key | The `REGISTRY_FULL` path |

Idle time is `Long.MAX_VALUE`, so a run does not evict entries. Eviction walks from the least-recently used entry and stops at the first entry that is still active, which is one comparison on these workloads. Capacity and refill are high enough that `hotKey` and `spreadKeys` stay on the allow path. `fullRegistryUnseenKey` repeats one absent key. It does not allocate a new key per call, and it does not exercise steady-state eviction.

Report throughput (operations/second) and sampled p95/p99 latency (microseconds) with the environment, the commit, and the raw JSON. Sampling estimates a distribution. The September 2026 table in [PERFORMANCE.md](../PERFORMANCE.md) used 1, 4, and 16 threads on a two-CPU runner, and that revision scanned every resident entry on each call. Compare it with runs of the same revision on a comparable host.

The measurements describe one JVM and this workload. Each process enforces its own limit.
