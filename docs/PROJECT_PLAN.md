# ChunkVault project plan

## Goal

ChunkVault is a single-node learning project that demonstrates storage fundamentals rather than file-sharing product features. It accepts a stream, cuts it into chunks, gives each chunk a SHA-256 identity, stores one copy of each distinct chunk, and records an ordered relational manifest that can reconstruct every file version.

## Core concepts

1. **Deduplication** removes repeated byte sequences from physical storage while retaining every logical reference to those bytes.
2. **File-level deduplication** only reuses a whole file when every byte is identical. **Chunk-level deduplication** can reuse unchanged regions between different files or versions.
3. **Content-addressable storage (CAS)** derives an object's address from its contents. A chunk hash such as `abcdef...` maps to `storage/ab/cd/abcdef...`.
4. **Cryptographic hashing** converts arbitrary bytes to a fixed-size digest. ChunkVault uses SHA-256 for stable addressing and integrity checks, not encryption.
5. **Chunk manifests** are ordered `FileChunk` rows containing chunk identity, index, offset, and length. Order is data, not an incidental query result.
6. **Reference management** is relational: `file_chunks` is the source of truth. Reference counts are calculated rather than blindly incremented/decremented.
7. **Garbage collection (GC)** reclaims chunks with zero manifest references after a grace period and a second in-transaction check.
8. **Fixed-size chunking** is simple and predictable but boundary shifts reduce reuse. **Content-defined chunking (CDC)** uses a Gear-style rolling fingerprint so boundaries usually re-synchronize after insertions.
9. **Integrity verification** re-hashes stored chunk bytes and reconstructed file bytes, reporting healthy, missing, or corrupted content.
10. **Concurrent uploads** can discover the same new hash together. Correctness comes from PostgreSQL `UNIQUE(hash)`, `INSERT ... ON CONFLICT DO NOTHING`, deterministic paths, and atomic publication of fully written chunk files.

## Delivery phases

| Phase | Deliverable | Exit condition |
| --- | --- | --- |
| Design | Architecture, ER model, references | Invariants and failure windows are explicit |
| Storage core | Chunkers, hashing, CAS writer | Bounded-memory chunk pipeline passes unit tests |
| Metadata | JPA model and Flyway schema | Ordered manifests and unique hashes are enforced by PostgreSQL |
| File lifecycle | Upload, version, download, delete | Files round-trip without whole-file buffering |
| Maintenance | Stats, verify, GC | Shared chunks survive deletion; unreferenced data is reclaimed safely |
| UI | Files, storage, details, comparison, integrity, GC | Every displayed value is returned by the backend |
| Proof | Tests, benchmark, audit | Claims are tied to tests or measured output |

## Key invariants

- A SHA-256 digest maps to at most one `chunks` row and one canonical path.
- A canonical chunk file is published only after its bytes are completely written and forced to disk where supported.
- Existing same-hash content must also have the same length and pass byte/hash verification before reuse.
- A manifest has exactly one row for each zero-based chunk index; downloads always order by that index.
- A chunk is GC-eligible only when no `file_chunks` row references it.
- Upload/manifest commits and GC are mutually excluded inside this single backend process.
- A failed metadata transaction may leave safe, unreferenced chunk bytes; it must never leave a manifest pointing to partial bytes.

## Intentionally excluded

Authentication, sharing, folders, previews, search, encryption, compression, replication, erasure coding, remote object stores, resumable multipart protocols, quotas, Kafka, Kubernetes, microservices, distributed locks, multi-node GC, and consensus are outside the project. Multiple backend replicas are unsupported because the maintenance lock is process-local.

## Definition of done

`docker compose up --build` starts PostgreSQL, the backend, and the frontend. A reviewer can upload a file and its duplicate, see unchanged physical bytes with increased logical bytes, download either file with the same checksum, inspect shared chunks, run verification, delete both logical files, and reclaim the now-unreferenced chunks.
