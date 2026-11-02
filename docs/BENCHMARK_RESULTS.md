# Benchmark results

These are measured results, not estimates. The run was executed on 2026-08-20 with `python3 scripts/benchmark.py` in the provided workspace.

## Scope

The deterministic harness exercises chunk boundary detection, SHA-256 hashing, one-file-per-hash local CAS publication with `fsync`, ordered manifest reconstruction, and whole-file checksum validation. It does **not** include multipart HTTP, PostgreSQL, Docker, browser rendering, or network transfer. Throughput therefore describes the storage-algorithm harness on this machine, not production capacity or an API SLA.

The benchmark profile uses four 8 MiB files per dataset and a 1 MiB fixed/target chunk so the suite finishes quickly. CDC uses 256 KiB minimum, 1 MiB target, and 2 MiB maximum. Production defaults are 4 MiB fixed and 1/4/8 MiB CDC.

## Recorded results

| Dataset | Strategy | Logical | Physical | Unique chunks | Duplicate chunks avoided | Upload MiB/s | Download MiB/s | Peak RSS MiB | Ratio |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| A — identical files | FIXED_SIZE | 32.00 MiB | 8.00 MiB | 8 | 24 | 281.23 | 525.91 | 79.8 | 4.000× |
| B — partial overlap | FIXED_SIZE | 32.00 MiB | 17.00 MiB | 17 | 15 | 257.15 | 570.37 | 79.8 | 1.882× |
| C — small insertions | FIXED_SIZE | 32.02 MiB | 32.02 MiB | 35 | 0 | 187.78 | 598.14 | 79.8 | 1.000× |
| C — small insertions | CONTENT_DEFINED | 32.02 MiB | 13.70 MiB | 12 | 24 | 5.26 | 307.81 | 79.8 | 2.337× |
| D — unrelated random | FIXED_SIZE | 32.00 MiB | 32.00 MiB | 32 | 0 | 101.64 | 372.85 | 79.8 | 1.000× |

Environment reported by the harness: Python 3.12.13, Linux 6.18.35 x86_64 with glibc 2.39. Peak RSS is the process high-water mark and is cumulative, so it is useful as a ceiling for this run rather than a per-dataset allocation measurement.

## Interpretation

- Identical content reached the expected 4.000× ratio: four logical copies occupied one physical copy.
- Partial overlap retained the five shared fixed-position chunks and stored each changed tail chunk once.
- Small insertions destroyed fixed boundary alignment, producing no reused chunks in this controlled workload.
- Gear CDC re-synchronized after insertions and reduced 32.02 MiB logical to 13.70 MiB physical, but its pure-Python per-byte boundary loop was much slower. This speed is not representative of the Java implementation.
- Random unrelated content correctly produced no deduplication benefit.

## Reproduce or scale

```bash
./scripts/run-benchmark.sh
./scripts/run-benchmark.sh --file-mib 32 --chunk-mib 4
```

Reruns will produce different throughput because of CPU load, filesystem cache, and storage hardware. Logical/physical/chunk counts remain deterministic for the same Python version and parameters.
