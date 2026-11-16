package com.chunkvault.integration;

import com.chunkvault.domain.Chunk;
import com.chunkvault.domain.ChunkingStrategy;
import com.chunkvault.dto.StorageStatsResponse;
import com.chunkvault.dto.UploadResponse;
import com.chunkvault.dto.VerifyRequest;
import com.chunkvault.dto.VerifyResponse;
import com.chunkvault.repository.ChunkRepository;
import com.chunkvault.service.FileDownloadService;
import com.chunkvault.service.FileQueryService;
import com.chunkvault.service.GarbageCollectionService;
import com.chunkvault.service.IntegrityVerificationService;
import com.chunkvault.service.StorageStatsService;
import com.chunkvault.service.UploadService;
import com.chunkvault.storage.ContentAddressableChunkStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class StorageLifecycleIntegrationTest {
    private static final Path STORAGE_ROOT = createStorageRoot();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("chunkvault_test").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("chunkvault.storage-root", () -> STORAGE_ROOT.toString());
        registry.add("chunkvault.max-upload-bytes", () -> 20 * 1024 * 1024);
        registry.add("chunkvault.fixed-chunk-size", () -> 1024);
        registry.add("chunkvault.cdc-min-size", () -> 1024);
        registry.add("chunkvault.cdc-target-size", () -> 2048);
        registry.add("chunkvault.cdc-max-size", () -> 4096);
        registry.add("chunkvault.gc-grace-period", () -> "PT0S");
    }

    @Autowired UploadService uploads;
    @Autowired FileDownloadService downloads;
    @Autowired FileQueryService files;
    @Autowired StorageStatsService stats;
    @Autowired GarbageCollectionService gc;
    @Autowired IntegrityVerificationService verification;
    @Autowired ChunkRepository chunks;
    @Autowired ContentAddressableChunkStore chunkStore;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanState() throws Exception {
        jdbc.execute("TRUNCATE TABLE gc_runs, upload_sessions, file_chunks, file_versions, stored_files, chunks RESTART IDENTITY CASCADE");
        if (Files.exists(STORAGE_ROOT)) {
            try (var paths = Files.walk(STORAGE_ROOT)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    if (!path.equals(STORAGE_ROOT)) Files.deleteIfExists(path);
                }
            }
        }
        Files.createDirectories(STORAGE_ROOT.resolve(".tmp"));
    }

    @Test
    void identicalUploadsIncreaseLogicalButNotPhysicalStorage() {
        byte[] data = randomBytes(3 * 1024, 11);
        upload("first.bin", data);
        StorageStatsResponse first = stats.getStats();
        upload("second.bin", data);
        StorageStatsResponse second = stats.getStats();

        assertThat(second.logicalBytes()).isEqualTo(first.logicalBytes() * 2);
        assertThat(second.physicalBytes()).isEqualTo(first.physicalBytes());
        assertThat(second.uniqueChunks()).isEqualTo(first.uniqueChunks());
        assertThat(second.duplicateChunksAvoided()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void partiallySimilarFilesReuseUnchangedFixedChunks() {
        byte[] a = filledChunk((byte) 'A');
        byte[] b = filledChunk((byte) 'B');
        byte[] c = filledChunk((byte) 'C');
        byte[] x = filledChunk((byte) 'X');
        upload("a.bin", join(a, b, c));
        upload("b.bin", join(a, x, c));

        assertThat(stats.getStats().uniqueChunks()).isEqualTo(4);
        assertThat(stats.getStats().totalChunksReferenced()).isEqualTo(6);
    }

    @Test
    void downloadReconstructsOriginalBytesInManifestOrder() throws Exception {
        byte[] original = randomBytes(5 * 1024 + 333, 23);
        UploadResponse uploaded = upload("round-trip.bin", original);
        FileDownloadService.DownloadDescriptor descriptor = downloads.prepare(uploaded.fileId(), 1);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        downloads.stream(descriptor, output);

        assertThat(output.toByteArray()).isEqualTo(original);
        assertThat(descriptor.fileHash()).isEqualTo(uploaded.fileHash());
    }

    @Test
    void deletingOneFileDoesNotRemoveChunksSharedByAnother() throws Exception {
        byte[] data = randomBytes(4096, 31);
        UploadResponse first = upload("one.bin", data);
        UploadResponse second = upload("two.bin", data);
        files.delete(first.fileId());
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        downloads.stream(downloads.prepare(second.fileId(), null), output);

        assertThat(output.toByteArray()).isEqualTo(data);
        assertThat(chunks.count()).isGreaterThan(0);
    }

    @Test
    void activeDownloadPinsChunksAcrossConcurrentLogicalDeletion() throws Exception {
        byte[] data = randomBytes(4096, 37);
        UploadResponse uploaded = upload("active-download.bin", data);
        FileDownloadService.DownloadDescriptor descriptor = downloads.prepare(uploaded.fileId(), null);
        files.delete(uploaded.fileId());

        assertThat(gc.collect().chunksDeleted()).isZero();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        downloads.stream(descriptor, output);
        assertThat(output.toByteArray()).isEqualTo(data);
        assertThat(gc.collect().chunksDeleted()).isGreaterThan(0);
    }

    @Test
    void deletingAllReferencesThenGcRemovesChunks() throws Exception {
        byte[] data = randomBytes(4096, 41);
        UploadResponse first = upload("one.bin", data);
        UploadResponse second = upload("two.bin", data);
        files.delete(first.fileId());
        files.delete(second.fileId());

        assertThat(gc.status().candidateChunks()).isGreaterThan(0);
        assertThat(gc.collect().chunksDeleted()).isGreaterThan(0);
        assertThat(chunks.count()).isZero();
        assertThat(chunkStore.listCanonicalFiles()).isEmpty();
    }

    @Test
    void concurrentIdenticalUploadsConvergeOnUniqueChunkRows() throws Exception {
        byte[] data = randomBytes(3 * 1024, 51);
        ExecutorService executor = Executors.newFixedThreadPool(6);
        List<Future<UploadResponse>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 6; i++) {
                int index = i;
                futures.add(executor.submit(() -> upload("concurrent-" + index + ".bin", data)));
            }
            for (Future<UploadResponse> future : futures) assertThat(future.get()).isNotNull();
        } finally {
            executor.shutdownNow();
        }

        assertThat(chunks.count()).isEqualTo(3);
        assertThat(stats.getStats().totalFiles()).isEqualTo(6);
        assertThat(chunkStore.listCanonicalFiles()).hasSize(3);
    }

    @Test
    void corruptionScannerDetectsModifiedCanonicalChunk() throws Exception {
        upload("damage-me.bin", randomBytes(2048, 61));
        Chunk chunk = chunks.findAll().getFirst();
        Files.write(chunkStore.resolveRelative(chunk.getStoragePath()), new byte[]{1, 2, 3},
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

        VerifyResponse result = verification.verify(new VerifyRequest(VerifyRequest.Scope.CHUNK,
                chunk.getHash(), null, null));

        assertThat(result.corrupted()).isEqualTo(1);
        assertThat(result.items().getFirst().status()).isEqualTo(VerifyResponse.Status.CORRUPTED);
    }

    private UploadResponse upload(String name, byte[] data) {
        return uploads.uploadNew(name, "application/octet-stream", data.length, ChunkingStrategy.FIXED_SIZE,
                new ByteArrayInputStream(data));
    }

    private static byte[] randomBytes(int size, long seed) { byte[] data = new byte[size]; new Random(seed).nextBytes(data); return data; }
    private static byte[] filledChunk(byte value) { byte[] data = new byte[1024]; Arrays.fill(data, value); return data; }
    private static byte[] join(byte[]... parts) { int size = Arrays.stream(parts).mapToInt(a -> a.length).sum(); byte[] result = new byte[size]; int offset = 0; for (byte[] part : parts) { System.arraycopy(part, 0, result, offset, part.length); offset += part.length; } return result; }
    private static Path createStorageRoot() { try { return Files.createTempDirectory("chunkvault-integration-"); } catch (Exception e) { throw new ExceptionInInitializerError(e); } }
}
