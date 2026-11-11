package com.chunkvault.repository;

import com.chunkvault.domain.StoredFile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from StoredFile f where f.id = :id")
    Optional<StoredFile> findByIdForUpdate(@Param("id") UUID id);
}
