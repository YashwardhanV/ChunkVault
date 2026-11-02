# ChunkVault interview guide

## Thirty-second explanation

“ChunkVault streams each upload through a chunker, hashes every chunk with SHA-256, and publishes complete chunks at deterministic paths. PostgreSQL stores one row per unique hash and an ordered manifest per file version. Repeated content creates another manifest reference instead of another physical chunk. Downloads stream the manifest in order; delete removes references; GC separately reclaims chunks that a transactional recount proves unreachable; verification re-hashes chunk and file bytes.”

## 1. Content-addressable storage and SHA-256

### What

Content-addressable storage names an object from its bytes instead of a user filename or generated location. ChunkVault uses the 64-character lowercase SHA-256 digest of raw chunk bytes.

### Why

Identical bytes deterministically produce the same address. Equality lookup, deduplication, immutable storage, and integrity checking become the same primitive.

### How

`Hashing` uses Java `MessageDigest`. `ContentAddressableChunkStore` maps hash `abcdef...` to `ab/cd/abcdef...`; fan-out prevents one huge directory. PostgreSQL enforces `UNIQUE(hash)`. Filenames never participate in identity.

### Tradeoffs

SHA-256 consumes CPU and an index lookup. A hash is not encryption and reveals equality. One-file-per-chunk has filesystem metadata overhead. Strong hashes make accidental collision infeasible but not mathematically impossible.

### Failure cases

- Existing hash, different size: treat as an integrity anomaly and abort.
- Existing path, changed bytes: re-hash on reuse and abort.
- SHA provider unavailable: fail startup/operation; do not silently downgrade.
- Genuine SHA-256 collision: this project cannot represent both chunks under one address.

### Alternatives

BLAKE3 is faster; SHA-512 is larger; non-cryptographic hashes are cheaper but unsafe as the only identity. A production adversarial store could use a secondary digest or byte comparison namespace.

### Important classes

`Hashing`, `ContentAddressableChunkStore`, `Chunk`, `ChunkRepository`.

### Likely questions

- Why SHA-256 instead of MD5 or SHA-1?
- Does matching SHA-256 prove two hostile inputs are equal?
- Why store the full hash when the path already contains it?

## 2. Chunking: fixed and content-defined

### What

Fixed-size chunking cuts every 4 MiB. Content-defined chunking (CDC) moves boundaries based on a rolling Gear fingerprint, within minimum/target/maximum bounds.

### Why

Whole-file deduplication only helps exact copies. Chunks reuse unchanged regions. Fixed boundaries are predictable but a one-byte insertion shifts every later boundary. CDC normally re-synchronizes after the changed neighborhood.

### How

`FixedSizeChunker` fills one bounded array using at most 64 KiB reads. `GearContentDefinedChunker` updates `fingerprint = (fingerprint << 1) + table[byte]`; after the minimum, low target-mask bits select a boundary, while the maximum forces one. SHA-256, not the rolling fingerprint, is the chunk identity.

Production defaults:

| Strategy | Minimum | Target | Maximum |
| --- | ---: | ---: | ---: |
| Fixed | 4 MiB | 4 MiB | 4 MiB |
| Content-defined | 1 MiB | 4 MiB | 8 MiB |

### Tradeoffs

Large chunks reduce rows/files/hash calls but miss small repeated regions. Small chunks improve reuse but increase metadata, seeks, and index work. CDC costs more CPU and has variable manifest length. The Gear implementation stays intentionally compact and understandable.

### Failure cases

Bad parameter ordering can create tiny or unbounded chunks; configuration validation prevents it. An attacker may influence CDC boundaries; this project is not a multi-tenant hostile storage service. Empty files correctly have a zero-row manifest and the SHA-256 of an empty stream.

### Alternatives

Other rolling-fingerprint designs, application-aware chunking, or whole-file CAS. Fixed remains the safest SDE-1 baseline.

### Important classes

`Chunker`, `FixedSizeChunker`, `GearContentDefinedChunker`, `ChunkerFactory`, `ChunkData`.

### Likely questions

- Show why an insertion harms fixed-size reuse.
- Why is the boundary fingerprint not used as the content ID?
- How would chunk size affect PostgreSQL and filesystem load?

## 3. Streaming I/O and hashing

### What

Upload, download, and verification process bounded buffers rather than whole-file byte arrays.

### Why

`MultipartFile.getBytes()` makes heap use proportional to file size and can cause OOM under concurrency. Streaming makes byte memory approximately one chunk plus a 64 KiB transfer buffer per request.

### How

The controller opens `MultipartFile.getInputStream()`. `LimitedInputStream` enforces the application limit while `DigestInputStream` computes the whole-file digest. The chunker emits one synchronous `ChunkData`; the store writes and closes it before the next chunk. Downloads copy each ordered chunk through a 64 KiB buffer to `StreamingResponseBody`.

### Tradeoffs

The in-memory manifest is proportional to chunk count, not bytes. A much larger system would batch manifest rows during ingestion. HTTP errors discovered after a streaming response starts cannot change the status code cleanly, so integrity scanning is a separate explicit operation.

### Failure cases

Client disconnects produce I/O failure. Maximum size can be exceeded even if `Content-Length` lies; `LimitedInputStream` catches that. Partial temp files are removed in `finally` or stale-temp GC.

### Alternatives

Reactive I/O could reduce blocked threads but adds complexity without changing storage invariants. Direct buffers and zero-copy transfers are optimizations, not requirements.

### Important classes

`UploadService`, `LimitedInputStream`, `FileDownloadService`, `IntegrityVerificationService`.

### Likely questions

- What is the memory complexity for a 2 GiB upload?
- Why keep a chunk array at all?
- How do you compute the file hash without rereading the upload?

## 4. Manifests and file versioning

### What

A file version is metadata plus ordered `FileChunk` rows. Each row records chunk, zero-based index, byte offset, and length. New versions create new manifests but reuse matching chunk rows.

### Why

CAS chunks have no original order or filename. The manifest turns a set of immutable chunks into a logical byte stream and makes version reuse visible.

### How

The database enforces unique `(file_version_id, chunk_index)`. Repository queries explicitly order by `chunkIndex`. Download additionally checks contiguous indices, offsets, and total size before streaming.

### Tradeoffs

Full manifests are simpler than delta manifests and make each version independently readable, but repeat metadata. At 4 MiB chunks, even a 2 GiB file has only about 512 rows.

### Failure cases

Missing indices, wrong offsets, or total-size mismatch stop download as integrity failures. Deleting a `StoredFile` cascades versions and manifest edges, never chunks.

### Alternatives

JSON manifests in CAS, delta manifests, Merkle trees, or Git-like trees. SQL rows are easiest to query and constrain here.

### Important classes

`StoredFile`, `FileVersion`, `FileChunk`, `ManifestCommitService`, `FileDownloadService`.

### Likely questions

- Why store both index and offset?
- How do two versions share data?
- Could reconstruction order rely on insertion order? (No.)

## 5. Concurrent uploads and unique constraints

### What

Two requests may calculate the same never-before-seen hash simultaneously.

### Why

A Java “find then insert” is a time-of-check/time-of-use race. Both requests can observe absence.

### How

Both publish to the same canonical path using non-replacing filesystem operations. Metadata uses PostgreSQL `UNIQUE(chunks.hash)` and `INSERT ... ON CONFLICT DO NOTHING`; each transaction then selects the winner and validates size/path. Correctness rests on the constraint, not the earlier observation.

### Tradeoffs

Contending transactions may wait on PostgreSQL's unique index. A hot repeated chunk serializes briefly at insertion. This is acceptable and much simpler than application-distributed locks.

### Failure cases

If one transaction rolls back, the other can insert. If both filesystem writers meet, one publishes and the other verifies/reuses. A filesystem lacking normal local hard-link/move semantics weakens publication guarantees and is outside support.

### Alternatives

PostgreSQL advisory locks, `SELECT FOR UPDATE` on a pre-existing key row, lock files, or an external coordinator. None replaces the unique constraint.

### Important classes

`ChunkRepository.insertIfAbsent`, `ManifestCommitService`, `ContentAddressableChunkStore`, `StorageLifecycleLock`.

### Likely questions

- Why is a unique constraint still required if paths are deterministic?
- What happens to the loser of `ON CONFLICT`?
- Would this work with two backend replicas?

## 6. Reference management, deletion, and garbage collection

### What

`file_chunks` edges are the persistent source of reference truth. Logical deletion removes edges. GC reclaims zero-reference chunk rows and files; short-lived in-process pins protect active downloads after logical deletion.

### Why

A mutable counter can drift when a transaction rolls back, code misses an update, or repair runs. Relational existence can prove reachability directly.

### How

Statistics and UI reference counts use SQL `COUNT`. GC takes the process write lock, finds candidates, skips active download pins, pessimistically locks each row, counts again in a new transaction, deletes metadata, commits, and then deletes the physical file. It also removes old canonical files with no metadata and stale session temp directories.

### Tradeoffs

Counting is more expensive than reading a counter. The process lock makes the reasoning clear but limits deployment to one backend. GC is bounded to 10,000 candidates per run.

### Failure cases

- Delete one of two files: shared edge remains, so chunk is not a candidate.
- Upload active: upload read lock prevents GC from entering.
- Candidate becomes referenced: in-transaction recount skips it.
- Filesystem delete fails after metadata commit: untracked bytes remain; no referenced data is lost; orphan sweep retries.

### Alternatives

Trigger-maintained counters plus reconciliation, mark-and-sweep reachability, tombstones/quarantine, or database advisory locks for multi-instance operation.

### Important classes

`FileQueryService.delete`, `GarbageCollectionService`, `GcTransactionService`, `StorageLifecycleLock`, `ActiveChunkPins`.

### Likely questions

- Why not delete chunks in the file-delete transaction?
- Can GC delete referenced data?
- How would you make GC safe across replicas?

## 7. Integrity verification

### What

Chunk verification reports `HEALTHY`, `MISSING`, or `CORRUPTED`. File verification streams all manifest chunks into a file digest and compares total size and stored file hash.

### Why

Content addressing can detect disk corruption only if bytes are reread and hashed. Database presence alone says nothing about file contents.

### How

`IntegrityVerificationService` computes actual chunk SHA-256 and length. Complete-file mode also verifies individual chunk identities while updating a reconstruction digest. Integration tests truncate a canonical chunk and assert `CORRUPTED`; there is no production corruption endpoint.

### Tradeoffs

Full scans consume disk bandwidth and compete with requests. This implementation is synchronous and intended for manual portfolio-scale use.

### Failure cases

Unreadable files are reported missing; length/hash mismatch is corrupted. A streaming download can still encounter a late disk error, which is why proactive verification matters.

### Alternatives

Scheduled sampling, background scrubbing, filesystem checksums, replicas for repair, or Merkle verification.

### Important classes

`IntegrityVerificationService`, `VerifyRequest`, `VerifyResponse`, `Hashing`.

### Likely questions

- Can detection repair corruption? (No; there is no replica.)
- Why verify size and hash?
- When would you run a full scan versus sampling?

## 8. Transactional metadata versus filesystem state

PostgreSQL and a normal filesystem do not share a transaction manager. There is no atomic commit across both. The project deliberately publishes complete immutable bytes **before** committing a manifest.

### What if the database transaction succeeds but the filesystem operation fails?

For uploads, this ordering prevents that state: filesystem publication happens first, and any publication failure aborts before `ManifestCommitService` runs. A committed manifest should therefore only point to already-published paths. Hardware loss after commit is still possible and verification reports it; no replica exists to repair it.

For GC, metadata deletion commits before physical deletion. If physical deletion then fails, an untracked orphan remains. That wastes space but does not make a referenced file unreadable. The next orphan sweep retries after the grace period.

### What if the filesystem write succeeds but the database transaction fails?

Canonical bytes may remain without metadata. They are safe because content is immutable and unreferenced. A later upload of the same chunk verifies and adopts the bytes; otherwise the old-untracked-file sweep reclaims them. Session temporary files are deleted in `finally` or by stale-temp cleanup.

### Why this asymmetry?

Storage correctness values “never expose metadata pointing to incomplete bytes” above “never leak temporary disk space.” Orphan space is recoverable; a committed broken manifest is user-visible data loss.

### More durable alternatives

- Transactional outbox rows plus an asynchronous finalizer and explicit `STAGING/AVAILABLE` states.
- Quarantine/trash rename with transaction synchronization.
- Store chunk bytes inside a transactional database (simple semantics, poor fit for large blobs).
- Use an object store with conditional puts and versioning, still coordinating metadata explicitly.

## 9. Logical versus physical storage

`logicalBytes = SUM(file_versions.total_size)`. `physicalBytes = SUM(chunks.size)` for managed canonical chunks. `deduplicatedBytes = logicalBytes - physicalBytes`. `deduplicationRatio = logicalBytes / physicalBytes` (zero when physical is zero). `duplicateChunksAvoided = manifest references - distinct referenced chunks`.

Unreferenced candidates still consume managed physical space until GC, so physical may temporarily exceed logical after logical deletion. “Physical contribution” in the file list means chunks reachable by that logical file and by no other file; it is not a fair-share allocation.

## 10. Defensible scope

The project stops before encryption, compression, remote backends, authentication, sharing, replication, erasure coding, distributed locks, resumable sessions, and multi-node GC. Those are valid extensions, but adding them would obscure the SDE-1 centerpiece: bytes, hashes, manifests, constraints, races, references, and recovery windows.

## Rapid-fire interview checklist

- A rolling hash chooses boundaries; SHA-256 chooses identity.
- Hash equality is defended with length and optional re-hash, not treated as encryption.
- A unique database constraint closes the concurrent insert race.
- A manifest query always has an explicit order.
- Deletion and reclamation are separate operations.
- Relational edges are reference truth.
- Filesystem-first upload ordering can leak space but not committed partial data.
- Database-first GC ordering can leak orphan bytes but not delete referenced data.
- One process lock is honest single-node scope, not distributed correctness.
- Every benchmark number names its dataset and excludes HTTP/PostgreSQL.
