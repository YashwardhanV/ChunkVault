package com.chunkvault.service;

import com.chunkvault.dto.StorageStatsResponse;
import com.chunkvault.repository.ChunkRepository;
import com.chunkvault.repository.FileChunkRepository;
import com.chunkvault.repository.FileVersionRepository;
import com.chunkvault.repository.StoredFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StorageStatsService {
    private final StoredFileRepository files;
    private final FileVersionRepository versions;
    private final ChunkRepository chunks;
    private final FileChunkRepository references;

    public StorageStatsService(StoredFileRepository files, FileVersionRepository versions,
                               ChunkRepository chunks, FileChunkRepository references) {
        this.files = files;
        this.versions = versions;
        this.chunks = chunks;
        this.references = references;
    }

    @Transactional(readOnly = true)
    public StorageStatsResponse getStats() {
        long logical = versions.sumLogicalBytes();
        long physical = chunks.sumPhysicalBytes();
        long totalReferences = references.count();
        long referencedUnique = references.countDistinctReferencedChunks();
        return new StorageStatsResponse(logical, physical, logical - physical,
                StatisticsCalculator.ratio(logical, physical), files.count(), versions.count(), totalReferences,
                chunks.count(), Math.max(0, totalReferences - referencedUnique));
    }
}
