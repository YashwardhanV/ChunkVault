package com.chunkvault.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stored_files")
public class StoredFile {
    @Id
    private UUID id;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StoredFile() {
    }

    public StoredFile(UUID id, String originalFilename, Instant createdAt) {
        this.id = id;
        this.originalFilename = originalFilename;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getOriginalFilename() { return originalFilename; }
    public Instant getCreatedAt() { return createdAt; }
}
