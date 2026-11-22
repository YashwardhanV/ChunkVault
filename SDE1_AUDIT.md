# ChunkVault SDE-1 audit

## Scorecard

Scores reflect portfolio readiness out of 10, not production maturity.

| Area | Score | Evidence | Remaining limit |
| --- | ---: | --- | --- |
| Java | 9 | Java 21 records, streams, NIO, bounded I/O, clear packages | No low-level async I/O |
| Spring Boot | 9 | MVC streaming, validation, JPA transactions, exception advice, config properties | No auth/observability depth |
| Streaming I/O | 9 | No whole-file upload/download arrays; 64 KiB reads; one bounded chunk | In-memory manifest grows with chunk count |
| Hashing | 9 | SHA-256 chunk/file digests, known-vector test, verification | No secondary collision namespace |
| Algorithms | 8 | Fixed and independent Gear CDC with boundary tests | Intentionally compact boundary tuning |
| PostgreSQL | 9 | Flyway, FKs, unique constraints, `ON CONFLICT`, row locks | No query-plan/load study |
| Concurrency | 8 | Unique-index race closure, concurrent upload test, lifecycle lock | One backend process only |
| Filesystem correctness | 8 | temp+force, non-replacing publication, deterministic paths, orphan recovery | Cross-platform atomic semantics vary |
| Testing | 9 | Unit + Mockito + Testcontainers lifecycle/race/corruption tests | Runtime coverage report not configured |
| Benchmarking | 8 | Reproducible deterministic datasets and honest scope | Harness excludes HTTP and PostgreSQL |
| React | 8 | Typed live dashboard, manifests, versions, integrity, GC | No component test suite |
| Docker | 9 | Three-service Compose, health checks, named volumes | No resource limits/backups |
| Interview defensibility | 9 | Explicit invariants, failure windows, tradeoffs, references | Candidate must be able to draw flows unaided |

## Audit answers

### 1. Is anything unnecessarily SDE-2/SDE-3?

No infrastructure is. CDC and filesystem/database recovery analysis are the hardest pieces, but they are bounded and teachable. The optional CDC could be omitted during a short interview; fixed chunks, unique constraints, manifests, and GC remain complete.

### 2. Is anything too tutorial-like?

The CRUD shell is ordinary, but the centerpiece is not: deterministic chunk publication, transaction/filesystem asymmetry, concurrent unique insertion, relational reference truth, streaming reconstruction, corruption experiments, and measured boundary-shift behavior go beyond a tutorial uploader.

### 3. Can uploads corrupt metadata?

Partial writes do not reach manifests. Complete chunks publish first; one transaction then inserts/validates chunk rows, file version, and ordered manifest. Transaction failure rolls metadata back. A failed upload can leave unreferenced immutable bytes, which orphan GC handles.

### 4. Can concurrent uploads store duplicate chunks incorrectly?

PostgreSQL `UNIQUE(hash)` prevents duplicate rows. `ON CONFLICT DO NOTHING` makes the loser reuse the winner. Deterministic non-replacing publication converges on one canonical path, and existing size/hash are verified. The integration test submits six identical uploads concurrently.

### 5. Can GC delete referenced data?

Within the supported one-process deployment, uploads hold a lifecycle read lock while GC holds the write lock. Active download descriptors also pin their hashes until streaming finishes. GC skips pins, row-locks each candidate, and recounts references inside the deletion transaction. It cannot cascade from logical deletion into chunks. Multi-replica deployment is explicitly unsupported.

### 6. Can files always be reconstructed in correct order?

The schema uniquely constrains each version/index, repository reads order by index, and download checks contiguous indices, offsets, and final size. A violated manifest stops rather than silently producing bytes.

### 7. Can corruption be detected?

Yes. Chunk scans compare actual size and SHA-256 with metadata. File scans reconstruct a whole-file digest. A Testcontainers integration test truncates real chunk bytes and expects `CORRUPTED`. Detection does not imply repair because there is no replica.

### 8. Are large files streamed?

Yes. Multipart content is opened as an `InputStream`; fixed chunk reads request at most 64 KiB into one chunk buffer; downloads stream each manifest chunk through 64 KiB. The 64 MiB generated-stream unit test confirms bounded read requests.

### 9. Can every resume claim be proven?

Version A maps to source and tests. Version B uses only values recorded in `docs/BENCHMARK_RESULTS.md`, produced by `scripts/benchmark.py`. The document clearly distinguishes the algorithm harness from end-to-end API throughput.

### 10. What are the 20 hardest interview questions?

1. Why does `findByHash` followed by `insert` fail under concurrency, and how exactly does the unique index fix it?
2. Walk through two threads publishing the same new chunk on both the filesystem and PostgreSQL sides.
3. Why publish bytes before committing upload metadata, but delete GC metadata before deleting bytes?
4. What recovery is possible after each crash point between temp write, canonical publication, and DB commit?
5. Can SHA-256 collision handling rely only on comparing lengths? What would full adversarial defense require?
6. Prove the upload heap use is bounded with respect to file size; what still grows?
7. Explain why a one-byte insertion destroys fixed-size reuse and how CDC re-synchronizes.
8. Why must a rolling boundary hash be separate from the cryptographic content hash?
9. How do min, target, and max CDC sizes affect variance, metadata, CPU, and deduplication?
10. Why derive reference truth from `file_chunks` instead of storing `referenceCount`?
11. Under what isolation/locking behavior can GC's second reference check still race?
12. How would you replace the JVM lifecycle lock when running three backend replicas?
13. What happens if an HTTP download has already sent headers when a later chunk is missing?
14. Why store manifest offsets if chunk order and sizes can derive them?
15. What does “physical contribution” mean for chunks shared across versions of the same file and other files?
16. Could an uncommitted PostgreSQL insert block another `ON CONFLICT` statement, and what happens after commit or rollback?
17. Which local filesystem guarantees does atomic/non-replacing publication rely on, and what changes on NFS or Windows?
18. Why can physical bytes temporarily exceed logical bytes after deletion, and is a negative `deduplicatedBytes` value necessarily a bug?
19. What does the benchmark prove, what does it not prove, and why is Python CDC throughput not a Java claim?
20. If a verifier finds a corrupt chunk shared by 100 versions, how would you add repair without weakening immutability?

## Final verdict

The project is appropriately ambitious for SDE-1 because the difficult parts are fundamental and explainable. It avoids distributed-system theater. The strongest interview story is the invariant chain: bounded stream → content boundary → SHA-256 identity → non-replacing immutable bytes → database-enforced uniqueness → ordered manifest → derived references → rechecked GC → digest verification.
