# Keyed registry benchmarks

Two recorded runs of `KeyedLimiterBenchmark`. Each table is a single-JVM microbenchmark of the host and workload recorded with it.

## GitHub-hosted run — 27 September 2026

Run: [GitHub Actions #36311129187](https://github.com/100lalitahegde-eng/token-bucket-java/actions/runs/36311129187). The `keyed-registry-jmh` artifact contains six JMH JSON files and `environment.txt`. Commit: `198e5574e1e4866a0ab0535c34a065d559efc770`. The figures below belong to that commit and that harness. That revision scanned every resident entry on each call.

Environment: GitHub-hosted Ubuntu runner, Linux x86-64, 2 visible CPUs (AMD EPYC 7763), Temurin 17.0.20.1. JMH 1.37; two forks; three one-second warmup iterations and five one-second measurement iterations per fork. Throughput and sampled latency are separate runs. Figures below are rounded from the artifact.

| Workload | Threads | Throughput (million ops/s) | Sample p95 (µs) | Sample p99 (µs) |
| --- | ---: | ---: | ---: | ---: |
| Hot key | 1 | 11.50 | 0.131 | 0.140 |
| Hot key | 4 | 9.95 | 7.440 | 9.392 |
| Hot key | 16 | 10.84 | 0.821 | 1.042 |
| Spread keys | 1 | 3.86 | 0.301 | 0.501 |
| Spread keys | 4 | 3.65 | 0.902 | 2.184 |
| Spread keys | 16 | 3.67 | 0.921 | 1.372 |
| Full registry, absent key | 1 | 0.300 | 5.584 | 6.552 |
| Full registry, absent key | 4 | 0.166 | 13.136 | 25.920 |
| Full registry, absent key | 16 | 0.164 | 10.688 | 22.240 |

The full-registry path in this revision scanned 1,024 resident entries on every request for the fixed absent key. The spread workload pre-creates 64 keys. Each worker then repeats the one key selected by its thread index, and every request takes the registry lock. Four and sixteen threads both exceed the runner's two visible CPUs, so those rows combine lock contention with oversubscription. Sampled percentiles move around on this host: hot-key p95 at four threads is higher than hot-key p95 at sixteen threads. A pause past p99 can pull the sample mean above p99.

`benchmarks/run.sh` now defaults to one thread and, when at least two processors are online, two threads. The workflow is manual and keeps the raw JSON and environment file as an artifact. The three-workload script was not re-run for the table above.

## Full registry, before and after idle-prefix eviction — 18 October 2026

Same benchmark method (`KeyedLimiterBenchmark.fullRegistryUnseenKey`), same JMH settings as the harness (1.37, two forks, three one-second warmup iterations, five one-second measurement iterations, no extra JVM flags). Throughput and sampled latency are separate runs. Threads are one, and two, matching `benchmarks/run.sh` on a machine with at least two processors.

Environment: Linux x86-64 VM, 4 vCPUs (Intel Xeon, KVM), Ubuntu OpenJDK 17.0.20.1. The before binary is commit `ecef218c72b9897ae3fb48f620ddff647b0d3393`, which scans every resident entry. The after binary is the access-ordered registry in this revision: `idleNanos` is `Long.MAX_VALUE`, so eviction inspects the eldest entry and stops. The registry still holds 1,024 keys, and the absent key still returns `REGISTRY_FULL`.

Throughput error is JMH's 99.9% confidence interval. Scores are rounded to the nearest operation/second. Latencies are rounded to three decimal microseconds.

| Revision | Threads | Throughput (ops/s) | Sample mean (µs) | Sample p95 (µs) | Sample p99 (µs) |
| --- | ---: | ---: | ---: | ---: | ---: |
| Full scan | 1 | 348,400 ± 6,355 | 2.911 ± 0.004 | 2.972 | 4.248 |
| Full scan | 2 | 265,253 ± 12,176 | 10.206 ± 0.527 | 18.048 | 42.560 |
| Idle prefix | 1 | 20,674,704 ± 583,730 | 0.077 ± 0.011 | 0.073 | 0.088 |
| Idle prefix | 2 | 8,310,444 ± 318,674 | 1.577 ± 0.068 | 14.512 | 18.880 |

At one thread the idle-prefix revision does about 59 times the throughput of the scan (20,674,704 / 348,400). At two threads the ratio is about 31 (8,310,444 / 265,253). Two threads yield less aggregate throughput than one thread on this VM, which matches a single registry lock. At one thread the idle-prefix p95 sits below the sample mean because a few longer pauses pull the mean up. At two threads the p95 stays in the same range as the scan (14.512 µs against 18.048 µs) while the median falls from 4.600 µs to 0.117 µs. Hot-key and spread-key workloads were not part of this pair of runs. This host is not the September runner, so the two tables are separate measurements.
