package com.chunkvault.repository;

import com.chunkvault.domain.FileChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface FileChunkRepository extends JpaRepository<FileChunk, Long> {
    List<FileChunk> findByFileVersionIdOrderByChunkIndex(UUID fileVersionId);
    long countByChunkId(UUID chunkId);

    @Query("select count(distinct fc.chunk.id) from FileChunk fc")
    long countDistinctReferencedChunks();
}
