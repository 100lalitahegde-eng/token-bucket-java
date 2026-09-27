# Exploratory keyed-registry benchmark — 27 September 2026

Run: [GitHub Actions #36311129187](https://github.com/100lalitahegde-eng/token-bucket-java/actions/runs/36311129187). The run's `keyed-registry-jmh` artifact contains all six JMH JSON files and `environment.txt`. Commit: `198e5574e1e4866a0ab0535c34a065d559efc770`.

Environment: GitHub-hosted Ubuntu runner, Linux x86-64, 2 visible CPUs (AMD EPYC 7763), Temurin 17.0.20.1. JMH 1.37; two forks; three one-second warm-up and five one-second measurement iterations per fork. Throughput and sampled latency are separate runs. Figures below are rounded from the artifact.

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

The full-registry path is materially more expensive in this run: it scans 1,024 resident entries on every absent-key request. Spread keys also share the registry lock, so independent keys do not imply parallel consumption in this implementation. The 16-thread cases oversubscribe the runner's two visible CPUs. Sampled percentile estimates vary substantially with contention (for example, hot-key p95 at four threads exceeds its 16-thread p95); no monotonic scaling claim follows from this one runner run. Extreme pauses beyond p99 can make the sample mean exceed p99.

These are exploratory microbenchmarks, not service latency, capacity planning, or a production guarantee. Repeat on a controlled machine, with workload mix and CPU limits recorded, before selecting an optimisation. Enforcement remains within one JVM.
