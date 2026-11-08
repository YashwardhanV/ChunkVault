package com.chunkvault.dto;

import java.math.BigDecimal;

public record StorageStatsResponse(long logicalBytes, long physicalBytes, long deduplicatedBytes,
                                   BigDecimal deduplicationRatio, long totalFiles, long totalFileVersions,
                                   long totalChunksReferenced, long uniqueChunks, long duplicateChunksAvoided) {
}
