package com.chunkvault.dto;

import java.time.Instant;

public record GcStatusResponse(long candidateChunks, long bytesReclaimable, Instant lastRunAt,
                               Long lastChunksDeleted, Long lastBytesReclaimed, Integer lastFailures) {
}
