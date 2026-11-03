# Resume bullets

## Version A — no benchmark metrics

- Built a content-addressable storage engine using streaming SHA-256 chunking, ordered manifest-based reconstruction, and PostgreSQL uniqueness constraints to deduplicate shared content across file uploads.
- Designed concurrency-safe chunk publication and reference-derived garbage collection so simultaneous uploads converge on one canonical chunk while deletion preserves data still referenced by other files or versions.
- Implemented fixed-size and Gear-based content-defined chunking, integrity scanning, file version comparison, a React storage dashboard, and JUnit/Testcontainers lifecycle tests.

## Version B — benchmark-backed

- Reduced physical storage from 32.00 MiB logical to 8.00 MiB on a controlled four-copy dataset by reusing 24 SHA-256-addressed chunk references, achieving a measured 4.000× deduplication ratio.
- Improved a controlled small-insertion version workload from 1.000× with fixed chunks to 2.337× with Gear content-defined chunks, reducing 32.02 MiB logical to 13.70 MiB physical.
- Measured 281.23 MiB/s ingest and 525.91 MiB/s reconstruction in the local CAS algorithm harness for the duplicate-heavy dataset, with every reconstructed file verified by SHA-256.
