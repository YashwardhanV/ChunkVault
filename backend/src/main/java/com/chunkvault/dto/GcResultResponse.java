package com.chunkvault.dto;

import java.time.Instant;

public record GcResultResponse(long chunksScanned, long chunksDeleted, long orphanFilesDeleted,
                               long bytesReclaimed, int failures, Instant completedAt) {
}
