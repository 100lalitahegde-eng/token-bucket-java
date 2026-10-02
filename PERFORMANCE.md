# Exploratory keyed-registry benchmark — 27 September 2026

Historical run: [GitHub Actions #36311129187](https://github.com/100lalitahegde-eng/token-bucket-java/actions/runs/36311129187). Its `keyed-registry-jmh` artifact contains six JMH JSON files and `environment.txt`. Commit: `198e5574e1e4866a0ab0535c34a065d559efc770`. The figures below belong to that commit and its original harness; they are not results from the revised default run.

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

In this run, the full-registry path scans 1,024 resident entries for each request for the fixed absent key. The spread workload pre-creates 64 keys but each worker repeatedly uses only one key selected by its thread index; every request still shares the registry lock. Four and sixteen threads both exceed the runner's two visible CPUs. These cases mix lock contention with CPU oversubscription and scheduling effects; they are not evidence of multicore scaling. Sampled percentile estimates vary substantially (for example, hot-key p95 at four threads exceeds its 16-thread p95). Extreme pauses beyond p99 can make the sample mean exceed p99.

The revised script defaults to one thread and, where at least two CPUs are visible, two threads. The workflow is manually dispatched and retains raw JSON and environment details as an artifact. No measurements of the revised default are reported here. These are exploratory one-JVM microbenchmarks, not service latency, capacity planning, or a production guarantee. Repeat on a controlled machine with workload mix, CPU limits, and background load recorded before selecting an optimisation. Enforcement remains within one JVM.
