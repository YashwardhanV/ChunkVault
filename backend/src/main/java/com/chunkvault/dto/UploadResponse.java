package com.chunkvault.dto;

import com.chunkvault.domain.ChunkingStrategy;

import java.time.Instant;
import java.util.UUID;

public record UploadResponse(UUID fileId, UUID versionId, int versionNumber, String filename, long totalSize,
                             String fileHash, int chunkCount, ChunkingStrategy chunkingStrategy,
                             long duplicateChunksReused, Instant createdAt) {
}
