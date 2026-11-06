package com.chunkvault.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gc_runs")
public class GcRun {
    @Id
    private UUID id;

    @Column(name = "chunks_scanned", nullable = false)
    private long chunksScanned;
    @Column(name = "chunks_deleted", nullable = false)
    private long chunksDeleted;
    @Column(name = "bytes_reclaimed", nullable = false)
    private long bytesReclaimed;
    @Column(nullable = false)
    private int failures;
    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    protected GcRun() {
    }

    public GcRun(UUID id, long chunksScanned, long chunksDeleted, long bytesReclaimed, int failures, Instant completedAt) {
        this.id = id;
        this.chunksScanned = chunksScanned;
        this.chunksDeleted = chunksDeleted;
        this.bytesReclaimed = bytesReclaimed;
        this.failures = failures;
        this.completedAt = completedAt;
    }

    public UUID getId() { return id; }
    public long getChunksScanned() { return chunksScanned; }
    public long getChunksDeleted() { return chunksDeleted; }
    public long getBytesReclaimed() { return bytesReclaimed; }
    public int getFailures() { return failures; }
    public Instant getCompletedAt() { return completedAt; }
}
