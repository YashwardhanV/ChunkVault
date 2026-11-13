package com.chunkvault.service;

import com.chunkvault.domain.ChunkingStrategy;
import com.chunkvault.storage.StoredChunk;

import java.util.List;

public record PreparedUpload(String filename, String contentType, long totalSize, String fileHash,
                             ChunkingStrategy strategy, List<StoredChunk> chunks) {
}
