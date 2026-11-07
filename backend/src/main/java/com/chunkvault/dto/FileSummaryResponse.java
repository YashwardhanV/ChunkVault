package com.chunkvault.dto;

import java.time.Instant;
import java.util.UUID;

public record FileSummaryResponse(UUID id, String filename, int latestVersion, long logicalSize,
                                  long exclusivePhysicalBytes, long versions, Instant createdAt) {
}
