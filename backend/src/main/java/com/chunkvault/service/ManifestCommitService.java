package com.chunkvault.service;

import com.chunkvault.domain.Chunk;
import com.chunkvault.domain.FileChunk;
import com.chunkvault.domain.FileVersion;
import com.chunkvault.domain.StoredFile;
import com.chunkvault.dto.UploadResponse;
import com.chunkvault.exception.IntegrityException;
import com.chunkvault.exception.ResourceNotFoundException;
import com.chunkvault.repository.ChunkRepository;
import com.chunkvault.repository.FileChunkRepository;
import com.chunkvault.repository.FileVersionRepository;
import com.chunkvault.repository.StoredFileRepository;
import com.chunkvault.storage.StoredChunk;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ManifestCommitService {
    private final StoredFileRepository storedFiles;
    private final FileVersionRepository versions;
    private final ChunkRepository chunks;
    private final FileChunkRepository manifest;

    public ManifestCommitService(StoredFileRepository storedFiles, FileVersionRepository versions,
                                 ChunkRepository chunks, FileChunkRepository manifest) {
        this.storedFiles = storedFiles;
        this.versions = versions;
        this.chunks = chunks;
        this.manifest = manifest;
    }

    @Transactional
    public UploadResponse createFile(PreparedUpload upload) {
        Instant now = Instant.now();
        StoredFile file = storedFiles.save(new StoredFile(UUID.randomUUID(), upload.filename(), now));
        return commitVersion(file, 1, upload, now);
    }

    @Transactional
    public UploadResponse createVersion(UUID fileId, PreparedUpload upload) {
        StoredFile file = storedFiles.findByIdForUpdate(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));
        int versionNumber = versions.maxVersionNumber(fileId) + 1;
        return commitVersion(file, versionNumber, upload, Instant.now());
    }

    private UploadResponse commitVersion(StoredFile file, int versionNumber, PreparedUpload upload, Instant now) {
        FileVersion version = versions.save(new FileVersion(UUID.randomUUID(), file, versionNumber,
                upload.totalSize(), upload.fileHash(), upload.contentType(), upload.strategy(), upload.chunks().size(), now));
        List<FileChunk> rows = new ArrayList<>(upload.chunks().size());
        long duplicates = 0;
        for (StoredChunk staged : upload.chunks()) {
            int inserted = chunks.insertIfAbsent(UUID.randomUUID(), staged.hash(), staged.size(), staged.relativePath(), now);
            if (inserted == 0) duplicates++;
            Chunk chunk = chunks.findByHash(staged.hash())
                    .orElseThrow(() -> new IllegalStateException("Chunk insert/select invariant failed: " + staged.hash()));
            if (chunk.getSize() != staged.size()) {
                throw new IntegrityException("Existing SHA-256 row has a different size: " + staged.hash());
            }
            if (!chunk.getStoragePath().equals(staged.relativePath())) {
                throw new IntegrityException("Existing SHA-256 row has a different canonical path: " + staged.hash());
            }
            rows.add(new FileChunk(version, chunk, staged.index(), staged.offset(), staged.size()));
        }
        manifest.saveAll(rows);
        return new UploadResponse(file.getId(), version.getId(), versionNumber, file.getOriginalFilename(),
                upload.totalSize(), upload.fileHash(), upload.chunks().size(), upload.strategy(), duplicates, now);
    }
}
