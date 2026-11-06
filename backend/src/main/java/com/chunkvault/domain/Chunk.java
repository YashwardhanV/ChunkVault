package com.chunkvault.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "chunks", uniqueConstraints = {
        @UniqueConstraint(name = "uq_chunk_hash", columnNames = "hash"),
        @UniqueConstraint(name = "uq_chunk_storage_path", columnNames = "storage_path")
})
public class Chunk {
    @Id
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64)
    private String hash;

    @Column(nullable = false)
    private long size;

    @Column(name = "storage_path", nullable = false, length = 180)
    private String storagePath;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Chunk() {
    }

    public UUID getId() { return id; }
    public String getHash() { return hash; }
    public long getSize() { return size; }
    public String getStoragePath() { return storagePath; }
    public Instant getCreatedAt() { return createdAt; }
}
