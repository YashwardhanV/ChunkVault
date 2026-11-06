package com.chunkvault.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "file_chunks", uniqueConstraints =
        @UniqueConstraint(name = "uq_manifest_chunk_index", columnNames = {"file_version_id", "chunk_index"}))
public class FileChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_version_id", nullable = false)
    private FileVersion fileVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chunk_id", nullable = false)
    private Chunk chunk;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "byte_offset", nullable = false)
    private long byteOffset;

    @Column(name = "chunk_size", nullable = false)
    private int chunkSize;

    protected FileChunk() {
    }

    public FileChunk(FileVersion fileVersion, Chunk chunk, int chunkIndex, long byteOffset, int chunkSize) {
        this.fileVersion = fileVersion;
        this.chunk = chunk;
        this.chunkIndex = chunkIndex;
        this.byteOffset = byteOffset;
        this.chunkSize = chunkSize;
    }

    public Long getId() { return id; }
    public FileVersion getFileVersion() { return fileVersion; }
    public Chunk getChunk() { return chunk; }
    public int getChunkIndex() { return chunkIndex; }
    public long getByteOffset() { return byteOffset; }
    public int getChunkSize() { return chunkSize; }
}
