package com.chunkvault.service;

import com.chunkvault.domain.Chunk;
import com.chunkvault.domain.FileChunk;
import com.chunkvault.domain.FileVersion;
import com.chunkvault.dto.VerifyRequest;
import com.chunkvault.dto.VerifyResponse;
import com.chunkvault.exception.ResourceNotFoundException;
import com.chunkvault.repository.ChunkRepository;
import com.chunkvault.repository.FileChunkRepository;
import com.chunkvault.repository.FileVersionRepository;
import com.chunkvault.storage.ContentAddressableChunkStore;
import com.chunkvault.util.Hashing;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.Lock;

@Service
public class IntegrityVerificationService {
    private final ChunkRepository chunks;
    private final FileVersionRepository versions;
    private final FileChunkRepository manifest;
    private final ContentAddressableChunkStore chunkStore;
    private final StorageLifecycleLock lifecycleLock;

    public IntegrityVerificationService(ChunkRepository chunks, FileVersionRepository versions,
                                        FileChunkRepository manifest, ContentAddressableChunkStore chunkStore,
                                        StorageLifecycleLock lifecycleLock) {
        this.chunks = chunks;
        this.versions = versions;
        this.manifest = manifest;
        this.chunkStore = chunkStore;
        this.lifecycleLock = lifecycleLock;
    }

    @Transactional(readOnly = true)
    public VerifyResponse verify(VerifyRequest request) {
        Lock lock = lifecycleLock.uploadLock();
        lock.lock();
        try {
            List<VerifyResponse.VerifyItem> items = switch (request.scope()) {
                case ALL_CHUNKS -> chunks.findAll(Sort.by("hash")).stream().map(this::verifyChunk).toList();
                case CHUNK -> List.of(verifyChunk(chunks.findByHash(require(request.chunkHash(), "chunkHash"))
                        .orElseThrow(() -> new ResourceNotFoundException("Chunk not found: " + request.chunkHash()))));
                case FILE -> List.of(verifyFile(require(request.fileId(), "fileId"), request.versionNumber()));
            };
            long healthy = items.stream().filter(i -> i.status() == VerifyResponse.Status.HEALTHY).count();
            long missing = items.stream().filter(i -> i.status() == VerifyResponse.Status.MISSING).count();
            long corrupted = items.size() - healthy - missing;
            return new VerifyResponse(healthy, missing, corrupted, items, Instant.now());
        } finally {
            lock.unlock();
        }
    }

    private VerifyResponse.VerifyItem verifyChunk(Chunk chunk) {
        Path path = chunkStore.resolveRelative(chunk.getStoragePath());
        if (!Files.isRegularFile(path)) {
            return new VerifyResponse.VerifyItem("CHUNK", chunk.getHash(), VerifyResponse.Status.MISSING,
                    chunk.getHash(), null, chunk.getSize(), "Canonical chunk file is missing");
        }
        try {
            long actualSize = Files.size(path);
            String actualHash = Hashing.sha256(path);
            boolean valid = actualSize == chunk.getSize() && actualHash.equals(chunk.getHash());
            return new VerifyResponse.VerifyItem("CHUNK", chunk.getHash(),
                    valid ? VerifyResponse.Status.HEALTHY : VerifyResponse.Status.CORRUPTED,
                    chunk.getHash(), actualHash, actualSize,
                    valid ? "Size and SHA-256 match" : "Stored bytes do not match metadata");
        } catch (IOException e) {
            return new VerifyResponse.VerifyItem("CHUNK", chunk.getHash(), VerifyResponse.Status.MISSING,
                    chunk.getHash(), null, chunk.getSize(), "Chunk cannot be read: " + e.getMessage());
        }
    }

    private VerifyResponse.VerifyItem verifyFile(UUID fileId, Integer versionNumber) {
        FileVersion version = versionNumber == null
                ? versions.findTopByStoredFileIdOrderByVersionNumberDesc(fileId)
                    .orElseThrow(() -> new ResourceNotFoundException("File/version not found: " + fileId))
                : versions.findByStoredFileIdAndVersionNumber(fileId, versionNumber)
                    .orElseThrow(() -> new ResourceNotFoundException("File/version not found: " + fileId));
        MessageDigest digest = Hashing.sha256Digest();
        byte[] buffer = new byte[64 * 1024];
        long totalSize = 0;
        List<String> failedChunks = new ArrayList<>();
        try {
            for (FileChunk row : manifest.findByFileVersionIdOrderByChunkIndex(version.getId())) {
                Path path = chunkStore.resolveRelative(row.getChunk().getStoragePath());
                if (!Files.isRegularFile(path)) {
                    return new VerifyResponse.VerifyItem("FILE", fileId + ":v" + version.getVersionNumber(),
                            VerifyResponse.Status.MISSING, version.getFileHash(), null, totalSize,
                            "Missing chunk " + row.getChunk().getHash());
                }
                MessageDigest chunkDigest = Hashing.sha256Digest();
                long chunkSize = 0;
                try (InputStream input = Files.newInputStream(path)) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        digest.update(buffer, 0, read);
                        chunkDigest.update(buffer, 0, read);
                        chunkSize += read;
                        totalSize += read;
                    }
                }
                if (chunkSize != row.getChunkSize()
                        || !Hashing.hex(chunkDigest.digest()).equals(row.getChunk().getHash())) {
                    failedChunks.add(row.getChunk().getHash());
                }
            }
            String actual = Hashing.hex(digest.digest());
            boolean valid = failedChunks.isEmpty() && totalSize == version.getTotalSize()
                    && actual.equals(version.getFileHash());
            return new VerifyResponse.VerifyItem("FILE", fileId + ":v" + version.getVersionNumber(),
                    valid ? VerifyResponse.Status.HEALTHY : VerifyResponse.Status.CORRUPTED,
                    version.getFileHash(), actual, totalSize,
                    valid ? "Reconstructed size and SHA-256 match"
                            : "Reconstruction mismatch; damaged chunks=" + failedChunks);
        } catch (IOException e) {
            return new VerifyResponse.VerifyItem("FILE", fileId + ":v" + version.getVersionNumber(),
                    VerifyResponse.Status.MISSING, version.getFileHash(), null, totalSize,
                    "File cannot be reconstructed: " + e.getMessage());
        }
    }

    private static <T> T require(T value, String name) {
        if (value == null || value instanceof String string && string.isBlank()) {
            throw new IllegalArgumentException(name + " is required for this verification scope");
        }
        return value;
    }
}
