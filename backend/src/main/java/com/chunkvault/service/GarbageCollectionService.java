package com.chunkvault.service;

import com.chunkvault.config.ChunkVaultProperties;
import com.chunkvault.domain.Chunk;
import com.chunkvault.domain.GcRun;
import com.chunkvault.dto.GcResultResponse;
import com.chunkvault.dto.GcStatusResponse;
import com.chunkvault.repository.ChunkRepository;
import com.chunkvault.repository.GcRunRepository;
import com.chunkvault.storage.ContentAddressableChunkStore;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.Lock;

@Service
public class GarbageCollectionService {
    private static final int MAX_CANDIDATES_PER_RUN = 10_000;
    private final ChunkVaultProperties properties;
    private final ChunkRepository chunks;
    private final GcRunRepository runs;
    private final GcTransactionService transactions;
    private final ContentAddressableChunkStore chunkStore;
    private final StorageLifecycleLock lifecycleLock;
    private final ActiveChunkPins activePins;

    public GarbageCollectionService(ChunkVaultProperties properties, ChunkRepository chunks, GcRunRepository runs,
                                    GcTransactionService transactions, ContentAddressableChunkStore chunkStore,
                                    StorageLifecycleLock lifecycleLock, ActiveChunkPins activePins) {
        this.properties = properties;
        this.chunks = chunks;
        this.runs = runs;
        this.transactions = transactions;
        this.chunkStore = chunkStore;
        this.lifecycleLock = lifecycleLock;
        this.activePins = activePins;
    }

    public GcResultResponse collect() {
        Lock lock = lifecycleLock.garbageCollectionLock();
        lock.lock();
        try {
            Instant cutoff = Instant.now().minus(properties.gcGracePeriod());
            List<Chunk> candidates = chunks.findGcCandidates(cutoff, PageRequest.of(0, MAX_CANDIDATES_PER_RUN));
            long metadataDeleted = 0;
            long orphanFilesDeleted = 0;
            long bytesReclaimed = 0;
            int failures = 0;
            for (Chunk candidate : candidates) {
                if (activePins.isPinned(candidate.getHash())) continue;
                Optional<GcTransactionService.DeletedChunk> deleted =
                        transactions.deleteMetadataIfStillUnreferenced(candidate.getId());
                if (deleted.isEmpty()) continue;
                metadataDeleted++;
                Path path = chunkStore.resolveRelative(deleted.get().relativePath());
                try {
                    if (Files.deleteIfExists(path)) bytesReclaimed += deleted.get().size();
                } catch (IOException e) {
                    failures++;
                }
            }

            try {
                for (Path path : chunkStore.listCanonicalFiles()) {
                    String hash = path.getFileName().toString();
                    if (!chunks.existsByHash(hash) && Files.getLastModifiedTime(path).toInstant().isBefore(cutoff)) {
                        long size = Files.size(path);
                        try {
                            if (Files.deleteIfExists(path)) {
                                orphanFilesDeleted++;
                                bytesReclaimed += size;
                            }
                        } catch (IOException e) {
                            failures++;
                        }
                    }
                }
            } catch (IOException e) {
                failures++;
            }
            chunkStore.cleanupStaleTemporaryDirectories(cutoff);
            Instant completed = Instant.now();
            runs.save(new GcRun(UUID.randomUUID(), candidates.size(), metadataDeleted, bytesReclaimed, failures, completed));
            return new GcResultResponse(candidates.size(), metadataDeleted, orphanFilesDeleted,
                    bytesReclaimed, failures, completed);
        } finally {
            lock.unlock();
        }
    }

    @Transactional(readOnly = true)
    public GcStatusResponse status() {
        Optional<GcRun> last = runs.findTopByOrderByCompletedAtDesc();
        return new GcStatusResponse(chunks.countGcCandidates(), chunks.sumGcCandidateBytes(),
                last.map(GcRun::getCompletedAt).orElse(null), last.map(GcRun::getChunksDeleted).orElse(null),
                last.map(GcRun::getBytesReclaimed).orElse(null), last.map(GcRun::getFailures).orElse(null));
    }
}
