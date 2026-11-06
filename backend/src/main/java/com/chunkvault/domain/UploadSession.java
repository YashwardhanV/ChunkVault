package com.chunkvault.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "upload_sessions")
public class UploadSession {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private UploadStatus status;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Enumerated(EnumType.STRING)
    @Column(name = "chunking_strategy", nullable = false, length = 32)
    private ChunkingStrategy chunkingStrategy;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected UploadSession() {
    }

    public UploadSession(UUID id, String originalFilename, ChunkingStrategy strategy) {
        this.id = id;
        this.status = UploadStatus.ACTIVE;
        this.originalFilename = originalFilename;
        this.chunkingStrategy = strategy;
        this.startedAt = Instant.now();
    }

    public void complete() {
        status = UploadStatus.COMPLETED;
        completedAt = Instant.now();
    }

    public void fail(String message) {
        status = UploadStatus.FAILED;
        errorMessage = message == null ? "Upload failed" : message.substring(0, Math.min(500, message.length()));
        completedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UploadStatus getStatus() { return status; }
    public String getOriginalFilename() { return originalFilename; }
    public ChunkingStrategy getChunkingStrategy() { return chunkingStrategy; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
