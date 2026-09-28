#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
mkdir -p benchmarks/results
{
  date -u '+UTC: %Y-%m-%dT%H:%M:%SZ'
  java -version 2>&1
  uname -a
  if command -v lscpu >/dev/null 2>&1; then lscpu; fi
} > benchmarks/results/environment.txt

mvn -B install
mvn -B -f benchmarks/pom.xml package
# Keep the default comparison within the CPUs visible to this process.
cpus=$(getconf _NPROCESSORS_ONLN)
threads=(1)
if (( cpus >= 2 )); then threads+=(2); fi
for count in "${threads[@]}"; do
  for mode in thrpt sample; do
    unit=s
    if [ "$mode" = sample ]; then unit=us; fi
    java -jar benchmarks/target/benchmarks.jar KeyedLimiterBenchmark \
      -t "$count" -bm "$mode" -tu "$unit" -rf json \
      -rff "benchmarks/results/${mode}-t${count}.json"
  done
done
