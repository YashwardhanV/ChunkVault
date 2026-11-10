package com.chunkvault.repository;

import com.chunkvault.domain.Chunk;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChunkRepository extends JpaRepository<Chunk, UUID> {
    Optional<Chunk> findByHash(String hash);
    boolean existsByHash(String hash);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO chunks (id, hash, size, storage_path, created_at)
            VALUES (:id, :hash, :size, :path, :createdAt)
            ON CONFLICT (hash) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("hash") String hash, @Param("size") long size,
                       @Param("path") String path, @Param("createdAt") Instant createdAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Chunk c where c.id = :id")
    Optional<Chunk> findByIdForUpdate(@Param("id") UUID id);

    @Query("select coalesce(sum(c.size), 0) from Chunk c")
    long sumPhysicalBytes();

    @Query("select count(c) from Chunk c where not exists (select fc.id from FileChunk fc where fc.chunk = c)")
    long countGcCandidates();

    @Query("select coalesce(sum(c.size), 0) from Chunk c where not exists (select fc.id from FileChunk fc where fc.chunk = c)")
    long sumGcCandidateBytes();

    @Query("select c from Chunk c where c.createdAt < :cutoff and not exists " +
            "(select fc.id from FileChunk fc where fc.chunk = c) order by c.createdAt")
    List<Chunk> findGcCandidates(@Param("cutoff") Instant cutoff, Pageable pageable);

    @Query(value = """
            SELECT COALESCE(SUM(c.size), 0)
            FROM chunks c
            WHERE EXISTS (
              SELECT 1 FROM file_chunks fc
              JOIN file_versions fv ON fv.id = fc.file_version_id
              WHERE fc.chunk_id = c.id AND fv.stored_file_id = :fileId
            )
            AND NOT EXISTS (
              SELECT 1 FROM file_chunks fc2
              JOIN file_versions fv2 ON fv2.id = fc2.file_version_id
              WHERE fc2.chunk_id = c.id AND fv2.stored_file_id <> :fileId
            )
            """, nativeQuery = true)
    long exclusivePhysicalBytes(@Param("fileId") UUID fileId);
}
