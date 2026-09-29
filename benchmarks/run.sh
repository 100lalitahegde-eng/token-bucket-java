#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
mkdir -p benchmarks/results
# Remove only files produced by the default thread counts on a previous local run.
rm -f benchmarks/results/{thrpt,sample}-t{1,2}.json
cpus=$(getconf _NPROCESSORS_ONLN)
threads=(1)
if (( cpus >= 2 )); then threads+=(2); fi
{
  printf "Online processors (getconf): %s\n" "$cpus"
  printf "JMH threads: %s\n" "${threads[*]}"
  date -u '+UTC: %Y-%m-%dT%H:%M:%SZ'
  java -version 2>&1
  uname -a
  if command -v lscpu >/dev/null 2>&1; then lscpu; fi
} > benchmarks/results/environment.txt

mvn -B install
mvn -B -f benchmarks/pom.xml package
for count in "${threads[@]}"; do
  for mode in thrpt sample; do
    unit=s
    if [ "$mode" = sample ]; then unit=us; fi
    java -jar benchmarks/target/benchmarks.jar KeyedLimiterBenchmark \
      -t "$count" -bm "$mode" -tu "$unit" -rf json \
      -rff "benchmarks/results/${mode}-t${count}.json"
  done
done
