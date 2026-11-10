package com.chunkvault.dto;

import com.chunkvault.domain.ChunkingStrategy;

import java.time.Instant;
import java.util.UUID;

public record VersionResponse(UUID id, int versionNumber, long totalSize, String fileHash, String contentType,
                              int chunkCount, ChunkingStrategy chunkingStrategy, Instant createdAt) {
}
