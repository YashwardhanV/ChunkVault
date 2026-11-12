package com.chunkvault.service;

import com.chunkvault.domain.FileChunk;
import com.chunkvault.domain.FileVersion;
import com.chunkvault.domain.StoredFile;
import com.chunkvault.dto.ChunkManifestItem;
import com.chunkvault.dto.FileDetailResponse;
import com.chunkvault.dto.FileSummaryResponse;
import com.chunkvault.dto.PageResponse;
import com.chunkvault.dto.VersionComparisonResponse;
import com.chunkvault.dto.VersionResponse;
import com.chunkvault.exception.ResourceNotFoundException;
import com.chunkvault.repository.ChunkRepository;
import com.chunkvault.repository.FileChunkRepository;
import com.chunkvault.repository.FileVersionRepository;
import com.chunkvault.repository.StoredFileRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class FileQueryService {
    private final StoredFileRepository files;
    private final FileVersionRepository versions;
    private final FileChunkRepository manifest;
    private final ChunkRepository chunks;

    public FileQueryService(StoredFileRepository files, FileVersionRepository versions,
                            FileChunkRepository manifest, ChunkRepository chunks) {
        this.files = files;
        this.versions = versions;
        this.manifest = manifest;
        this.chunks = chunks;
    }

    @Transactional(readOnly = true)
    public PageResponse<FileSummaryResponse> list(int page, int size) {
        Page<StoredFile> result = files.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<FileSummaryResponse> content = result.getContent().stream().map(file -> {
            List<FileVersion> fileVersions = versions.findByStoredFileIdOrderByVersionNumberDesc(file.getId());
            FileVersion latest = fileVersions.getFirst();
            return new FileSummaryResponse(file.getId(), file.getOriginalFilename(), latest.getVersionNumber(),
                    latest.getTotalSize(), chunks.exclusivePhysicalBytes(file.getId()), fileVersions.size(), file.getCreatedAt());
        }).toList();
        return PageResponse.from(result, content);
    }

    @Transactional(readOnly = true)
    public FileDetailResponse detail(UUID fileId, Integer requestedVersion) {
        StoredFile file = getFile(fileId);
        List<FileVersion> fileVersions = versions.findByStoredFileIdOrderByVersionNumberDesc(fileId);
        if (fileVersions.isEmpty()) throw new IllegalStateException("Stored file has no version");
        FileVersion selected = requestedVersion == null
                ? fileVersions.getFirst()
                : versions.findByStoredFileIdAndVersionNumber(fileId, requestedVersion)
                    .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + requestedVersion));
        List<ChunkManifestItem> items = manifest.findByFileVersionIdOrderByChunkIndex(selected.getId()).stream()
                .map(row -> {
                    long references = manifest.countByChunkId(row.getChunk().getId());
                    return new ChunkManifestItem(row.getChunkIndex(), row.getByteOffset(), row.getChunkSize(),
                            row.getChunk().getHash(), references, references > 1);
                }).toList();
        return new FileDetailResponse(file.getId(), file.getOriginalFilename(), file.getCreatedAt(),
                fileVersions.stream().map(FileQueryService::toVersion).toList(), toVersion(selected), items);
    }

    @Transactional(readOnly = true)
    public List<VersionResponse> versions(UUID fileId) {
        getFile(fileId);
        return versions.findByStoredFileIdOrderByVersionNumberDesc(fileId).stream()
                .map(FileQueryService::toVersion).toList();
    }

    @Transactional(readOnly = true)
    public VersionComparisonResponse compare(UUID fileId, int from, int to) {
        getFile(fileId);
        FileVersion fromVersion = getVersion(fileId, from);
        FileVersion toVersion = getVersion(fileId, to);
        Set<String> originalHashes = new HashSet<>();
        manifest.findByFileVersionIdOrderByChunkIndex(fromVersion.getId())
                .forEach(row -> originalHashes.add(row.getChunk().getHash()));
        List<VersionComparisonResponse.ComparedChunk> compared = new ArrayList<>();
        long reused = 0;
        for (FileChunk row : manifest.findByFileVersionIdOrderByChunkIndex(toVersion.getId())) {
            boolean isReused = originalHashes.contains(row.getChunk().getHash());
            if (isReused) reused++;
            compared.add(new VersionComparisonResponse.ComparedChunk(row.getChunkIndex(), row.getChunk().getHash(),
                    row.getChunkSize(), isReused));
        }
        BigDecimal ratio = compared.isEmpty() ? BigDecimal.ZERO.setScale(4)
                : BigDecimal.valueOf(reused).divide(BigDecimal.valueOf(compared.size()), 4, RoundingMode.HALF_UP);
        return new VersionComparisonResponse(from, to, reused, compared.size(), ratio, compared);
    }

    @Transactional
    public void delete(UUID fileId) {
        StoredFile file = getFile(fileId);
        files.delete(file);
    }

    private StoredFile getFile(UUID fileId) {
        return files.findById(fileId).orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));
    }

    private FileVersion getVersion(UUID fileId, int version) {
        return versions.findByStoredFileIdAndVersionNumber(fileId, version)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + version));
    }

    static VersionResponse toVersion(FileVersion version) {
        return new VersionResponse(version.getId(), version.getVersionNumber(), version.getTotalSize(),
                version.getFileHash(), version.getContentType(), version.getChunkCount(),
                version.getChunkingStrategy(), version.getCreatedAt());
    }
}
