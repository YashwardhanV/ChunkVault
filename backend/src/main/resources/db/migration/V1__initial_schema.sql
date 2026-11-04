CREATE TABLE stored_files (
    id UUID PRIMARY KEY,
    original_filename VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE file_versions (
    id UUID PRIMARY KEY,
    stored_file_id UUID NOT NULL REFERENCES stored_files(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    total_size BIGINT NOT NULL CHECK (total_size >= 0),
    file_hash CHAR(64) NOT NULL,
    content_type VARCHAR(160) NOT NULL,
    chunking_strategy VARCHAR(32) NOT NULL,
    chunk_count INTEGER NOT NULL CHECK (chunk_count >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_file_version_number UNIQUE (stored_file_id, version_number)
);

CREATE TABLE chunks (
    id UUID PRIMARY KEY,
    hash CHAR(64) NOT NULL,
    size BIGINT NOT NULL CHECK (size > 0),
    storage_path VARCHAR(180) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_chunk_hash UNIQUE (hash),
    CONSTRAINT uq_chunk_storage_path UNIQUE (storage_path)
);

CREATE TABLE file_chunks (
    id BIGSERIAL PRIMARY KEY,
    file_version_id UUID NOT NULL REFERENCES file_versions(id) ON DELETE CASCADE,
    chunk_id UUID NOT NULL REFERENCES chunks(id) ON DELETE RESTRICT,
    chunk_index INTEGER NOT NULL CHECK (chunk_index >= 0),
    byte_offset BIGINT NOT NULL CHECK (byte_offset >= 0),
    chunk_size INTEGER NOT NULL CHECK (chunk_size > 0),
    CONSTRAINT uq_manifest_chunk_index UNIQUE (file_version_id, chunk_index)
);

CREATE INDEX idx_file_chunks_chunk_id ON file_chunks(chunk_id);
CREATE INDEX idx_file_versions_file_id ON file_versions(stored_file_id);

CREATE TABLE upload_sessions (
    id UUID PRIMARY KEY,
    status VARCHAR(24) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    chunking_strategy VARCHAR(32) NOT NULL,
    error_message VARCHAR(500),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);

CREATE TABLE gc_runs (
    id UUID PRIMARY KEY,
    chunks_scanned BIGINT NOT NULL,
    chunks_deleted BIGINT NOT NULL,
    bytes_reclaimed BIGINT NOT NULL,
    failures INTEGER NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL
);
