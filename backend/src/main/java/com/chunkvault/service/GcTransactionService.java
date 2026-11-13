package com.chunkvault.service;

import com.chunkvault.domain.Chunk;
import com.chunkvault.repository.ChunkRepository;
import com.chunkvault.repository.FileChunkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class GcTransactionService {
    private final ChunkRepository chunks;
    private final FileChunkRepository references;

    public GcTransactionService(ChunkRepository chunks, FileChunkRepository references) {
        this.chunks = chunks;
        this.references = references;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<DeletedChunk> deleteMetadataIfStillUnreferenced(UUID chunkId) {
        Chunk chunk = chunks.findByIdForUpdate(chunkId).orElse(null);
        if (chunk == null || references.countByChunkId(chunkId) != 0) return Optional.empty();
        DeletedChunk deleted = new DeletedChunk(chunk.getStoragePath(), chunk.getSize());
        chunks.delete(chunk);
        chunks.flush();
        return Optional.of(deleted);
    }

    public record DeletedChunk(String relativePath, long size) { }
}
