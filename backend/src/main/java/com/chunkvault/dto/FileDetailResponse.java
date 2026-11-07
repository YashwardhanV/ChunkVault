package com.chunkvault.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FileDetailResponse(UUID id, String filename, Instant createdAt, List<VersionResponse> versions,
                                 VersionResponse selectedVersion, List<ChunkManifestItem> manifest) {
}
