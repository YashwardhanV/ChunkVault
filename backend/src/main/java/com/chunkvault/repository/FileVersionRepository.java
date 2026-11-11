package com.chunkvault.repository;

import com.chunkvault.domain.FileVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileVersionRepository extends JpaRepository<FileVersion, UUID> {
    Optional<FileVersion> findTopByStoredFileIdOrderByVersionNumberDesc(UUID storedFileId);
    Optional<FileVersion> findByStoredFileIdAndVersionNumber(UUID storedFileId, int versionNumber);
    List<FileVersion> findByStoredFileIdOrderByVersionNumberDesc(UUID storedFileId);

    @Query("select coalesce(max(v.versionNumber), 0) from FileVersion v where v.storedFile.id = :fileId")
    int maxVersionNumber(@Param("fileId") UUID fileId);

    @Query("select coalesce(sum(v.totalSize), 0) from FileVersion v")
    long sumLogicalBytes();
}
