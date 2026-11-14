package com.chunkvault.service;

import com.chunkvault.config.ChunkVaultProperties;
import com.chunkvault.domain.ChunkingStrategy;
import com.chunkvault.domain.UploadSession;
import com.chunkvault.dto.UploadResponse;
import com.chunkvault.exception.StorageOperationException;
import com.chunkvault.exception.UploadTooLargeException;
import com.chunkvault.repository.UploadSessionRepository;
import com.chunkvault.storage.Chunker;
import com.chunkvault.storage.ChunkerFactory;
import com.chunkvault.storage.ContentAddressableChunkStore;
import com.chunkvault.storage.StoredChunk;
import com.chunkvault.util.Hashing;
import com.chunkvault.util.LimitedInputStream;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.Lock;

@Service
public class UploadService {
    private final ChunkVaultProperties properties;
    private final ChunkerFactory chunkers;
    private final ContentAddressableChunkStore chunkStore;
    private final UploadSessionRepository sessions;
    private final ManifestCommitService manifests;
    private final StorageLifecycleLock lifecycleLock;

    public UploadService(ChunkVaultProperties properties, ChunkerFactory chunkers,
                         ContentAddressableChunkStore chunkStore, UploadSessionRepository sessions,
                         ManifestCommitService manifests, StorageLifecycleLock lifecycleLock) {
        this.properties = properties;
        this.chunkers = chunkers;
        this.chunkStore = chunkStore;
        this.sessions = sessions;
        this.manifests = manifests;
        this.lifecycleLock = lifecycleLock;
    }

    public UploadResponse uploadNew(String filename, String contentType, long declaredSize,
                                    ChunkingStrategy strategy, InputStream input) {
        return process(null, filename, contentType, declaredSize, strategy, input);
    }

    public UploadResponse uploadVersion(UUID fileId, String filename, String contentType, long declaredSize,
                                        ChunkingStrategy strategy, InputStream input) {
        return process(fileId, filename, contentType, declaredSize, strategy, input);
    }

    private UploadResponse process(UUID fileId, String filename, String contentType, long declaredSize,
                                   ChunkingStrategy strategy, InputStream input) {
        if (declaredSize > properties.maxUploadBytes()) throw new UploadTooLargeException(properties.maxUploadBytes());
        String safeFilename = sanitizeFilename(filename);
        ChunkingStrategy selectedStrategy = strategy == null ? ChunkingStrategy.FIXED_SIZE : strategy;
        UUID sessionId = UUID.randomUUID();
        UploadSession session = sessions.save(new UploadSession(sessionId, safeFilename, selectedStrategy));
        Lock lock = lifecycleLock.uploadLock();
        lock.lock();
        try {
            MessageDigest fileDigest = Hashing.sha256Digest();
            LimitedInputStream limited = new LimitedInputStream(input, properties.maxUploadBytes());
            DigestInputStream digested = new DigestInputStream(limited, fileDigest);
            Chunker chunker = chunkers.forStrategy(selectedStrategy);
            List<StoredChunk> stored = new ArrayList<>();
            chunker.split(digested, chunk -> stored.add(chunkStore.store(sessionId, chunk)));
            PreparedUpload prepared = new PreparedUpload(safeFilename, normalizeContentType(contentType), limited.count(),
                    Hashing.hex(fileDigest.digest()), selectedStrategy, List.copyOf(stored));
            UploadResponse response = fileId == null
                    ? manifests.createFile(prepared)
                    : manifests.createVersion(fileId, prepared);
            session.complete();
            sessions.save(session);
            return response;
        } catch (IOException e) {
            session.fail(e.getMessage());
            sessions.save(session);
            throw new StorageOperationException("Unable to store uploaded bytes", e);
        } catch (RuntimeException e) {
            session.fail(e.getMessage());
            sessions.save(session);
            throw e;
        } finally {
            chunkStore.cleanupSession(sessionId);
            lock.unlock();
        }
    }

    static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "unnamed.bin";
        String normalized = filename.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (normalized.isBlank()) normalized = "unnamed.bin";
        return normalized.substring(0, Math.min(255, normalized.length()));
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) return "application/octet-stream";
        return contentType.substring(0, Math.min(160, contentType.length()));
    }
}
