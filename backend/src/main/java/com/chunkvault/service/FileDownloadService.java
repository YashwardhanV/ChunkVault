package com.chunkvault.service;

import com.chunkvault.domain.FileChunk;
import com.chunkvault.domain.FileVersion;
import com.chunkvault.domain.StoredFile;
import com.chunkvault.exception.IntegrityException;
import com.chunkvault.exception.ResourceNotFoundException;
import com.chunkvault.repository.FileChunkRepository;
import com.chunkvault.repository.FileVersionRepository;
import com.chunkvault.repository.StoredFileRepository;
import com.chunkvault.storage.ContentAddressableChunkStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.Lock;

@Service
public class FileDownloadService {
    private static final int COPY_BUFFER = 64 * 1024;
    private final StoredFileRepository files;
    private final FileVersionRepository versions;
    private final FileChunkRepository manifest;
    private final ContentAddressableChunkStore chunkStore;
    private final ActiveChunkPins activePins;
    private final StorageLifecycleLock lifecycleLock;

    public FileDownloadService(StoredFileRepository files, FileVersionRepository versions,
                               FileChunkRepository manifest, ContentAddressableChunkStore chunkStore,
                               ActiveChunkPins activePins, StorageLifecycleLock lifecycleLock) {
        this.files = files;
        this.versions = versions;
        this.manifest = manifest;
        this.chunkStore = chunkStore;
        this.activePins = activePins;
        this.lifecycleLock = lifecycleLock;
    }

    @Transactional(readOnly = true)
    public DownloadDescriptor prepare(UUID fileId, Integer versionNumber) {
        Lock lock = lifecycleLock.uploadLock();
        lock.lock();
        try {
            StoredFile file = files.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));
            FileVersion version = versionNumber == null
                ? versions.findTopByStoredFileIdOrderByVersionNumberDesc(fileId)
                    .orElseThrow(() -> new ResourceNotFoundException("No file version: " + fileId))
                : versions.findByStoredFileIdAndVersionNumber(fileId, versionNumber)
                    .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + versionNumber));
            List<DownloadChunk> chunks = new ArrayList<>();
            int expectedIndex = 0;
            long expectedOffset = 0;
            for (FileChunk row : manifest.findByFileVersionIdOrderByChunkIndex(version.getId())) {
                if (row.getChunkIndex() != expectedIndex || row.getByteOffset() != expectedOffset) {
                    throw new IntegrityException("Manifest order/offset invariant failed for version " + version.getId());
                }
                chunks.add(new DownloadChunk(chunkStore.resolveRelative(row.getChunk().getStoragePath()),
                        row.getChunkSize(), row.getChunkIndex(), row.getChunk().getHash()));
                expectedIndex++;
                expectedOffset += row.getChunkSize();
            }
            if (expectedOffset != version.getTotalSize()) {
                throw new IntegrityException("Manifest size does not equal stored file size");
            }
            DownloadDescriptor descriptor = new DownloadDescriptor(file.getOriginalFilename(), version.getContentType(),
                    version.getTotalSize(), version.getFileHash(), List.copyOf(chunks));
            activePins.pin(descriptor.chunks().stream().map(DownloadChunk::hash).toList());
            return descriptor;
        } finally {
            lock.unlock();
        }
    }

    public void stream(DownloadDescriptor descriptor, OutputStream output) throws IOException {
        try {
            byte[] buffer = new byte[COPY_BUFFER];
            for (DownloadChunk chunk : descriptor.chunks()) {
                if (!Files.isRegularFile(chunk.path())) {
                    throw new IntegrityException("Missing chunk during download: " + chunk.hash());
                }
                long written = 0;
                try (InputStream input = Files.newInputStream(chunk.path())) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                        written += read;
                    }
                }
                if (written != chunk.size()) throw new IntegrityException("Chunk size changed during download: " + chunk.hash());
            }
        } finally {
            activePins.unpin(descriptor.chunks().stream().map(DownloadChunk::hash).toList());
        }
    }

    public record DownloadDescriptor(String filename, String contentType, long size, String fileHash,
                                     List<DownloadChunk> chunks) { }
    public record DownloadChunk(Path path, int size, int index, String hash) { }
}
