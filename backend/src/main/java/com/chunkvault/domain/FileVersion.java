package com.chunkvault.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "file_versions", uniqueConstraints =
        @UniqueConstraint(name = "uq_file_version_number", columnNames = {"stored_file_id", "version_number"}))
public class FileVersion {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stored_file_id", nullable = false)
    private StoredFile storedFile;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "total_size", nullable = false)
    private long totalSize;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Column(name = "content_type", nullable = false, length = 160)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "chunking_strategy", nullable = false, length = 32)
    private ChunkingStrategy chunkingStrategy;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FileVersion() {
    }

    public FileVersion(UUID id, StoredFile storedFile, int versionNumber, long totalSize, String fileHash,
                       String contentType, ChunkingStrategy chunkingStrategy, int chunkCount, Instant createdAt) {
        this.id = id;
        this.storedFile = storedFile;
        this.versionNumber = versionNumber;
        this.totalSize = totalSize;
        this.fileHash = fileHash;
        this.contentType = contentType;
        this.chunkingStrategy = chunkingStrategy;
        this.chunkCount = chunkCount;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public StoredFile getStoredFile() { return storedFile; }
    public int getVersionNumber() { return versionNumber; }
    public long getTotalSize() { return totalSize; }
    public String getFileHash() { return fileHash; }
    public String getContentType() { return contentType; }
    public ChunkingStrategy getChunkingStrategy() { return chunkingStrategy; }
    public int getChunkCount() { return chunkCount; }
    public Instant getCreatedAt() { return createdAt; }
}
