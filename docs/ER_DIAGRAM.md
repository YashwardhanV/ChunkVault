# Metadata model

```mermaid
erDiagram
  STORED_FILE ||--o{ FILE_VERSION : has
  FILE_VERSION ||--|{ FILE_CHUNK : orders
  CHUNK ||--o{ FILE_CHUNK : referenced_by

  STORED_FILE {
    uuid id PK
    varchar original_filename
    timestamptz created_at
  }
  FILE_VERSION {
    uuid id PK
    uuid stored_file_id FK
    int version_number
    bigint total_size
    char64 file_hash
    varchar chunking_strategy
    int chunk_count
    timestamptz created_at
  }
  CHUNK {
    uuid id PK
    char64 hash UK
    bigint size
    varchar storage_path
    timestamptz created_at
  }
  FILE_CHUNK {
    bigint id PK
    uuid file_version_id FK
    uuid chunk_id FK
    int chunk_index
    bigint byte_offset
    int chunk_size
  }
  UPLOAD_SESSION {
    uuid id PK
    varchar status
    varchar original_filename
    varchar chunking_strategy
    varchar error_message
    timestamptz started_at
    timestamptz completed_at
  }
  GC_RUN {
    uuid id PK
    bigint chunks_scanned
    bigint chunks_deleted
    bigint bytes_reclaimed
    int failures
    timestamptz completed_at
  }
```

Important database constraints:

- `chunks(hash)` is unique: the database, not a pre-insert lookup, resolves upload races.
- `file_versions(stored_file_id, version_number)` is unique.
- `file_chunks(file_version_id, chunk_index)` is unique and the download query explicitly orders by `chunk_index`.
- `file_chunks` foreign keys prevent metadata from referencing nonexistent chunks.
- Cascade deletion stops at the manifest: deleting a file cascades through its versions and `file_chunks`, but never through to `chunks`.

`referenceCount` is intentionally absent from `chunks`. Actual references are derived with `COUNT(file_chunks.id)`, preventing counter drift.
