package com.chunkvault.dto;

import java.math.BigDecimal;
import java.util.List;

public record VersionComparisonResponse(int fromVersion, int toVersion, long reusedChunks, long totalTargetChunks,
                                        BigDecimal reuseRatio, List<ComparedChunk> targetManifest) {
    public record ComparedChunk(int index, String hash, int size, boolean reused) { }
}
