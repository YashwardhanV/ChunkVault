package com.chunkvault.service;

import com.chunkvault.config.ChunkVaultProperties;
import com.chunkvault.domain.ChunkingStrategy;
import com.chunkvault.domain.UploadSession;
import com.chunkvault.dto.UploadResponse;
import com.chunkvault.repository.UploadSessionRepository;
import com.chunkvault.storage.ChunkerFactory;
import com.chunkvault.storage.ContentAddressableChunkStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadServiceTest {
    @TempDir Path temp;
    @Mock UploadSessionRepository sessions;
    @Mock ManifestCommitService manifests;

    @Test
    void streamsChunksThenCommitsPreparedManifest() throws Exception {
        ChunkVaultProperties properties = new ChunkVaultProperties(temp, 1_000_000, 1024,
                1024, 2048, 4096, Duration.ZERO, true, "http://localhost:3000");
        ContentAddressableChunkStore store = new ContentAddressableChunkStore(properties);
        store.initialize();
        when(sessions.save(any(UploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UploadResponse expected = new UploadResponse(UUID.randomUUID(), UUID.randomUUID(), 1, "safe.bin", 2048,
                "hash", 2, ChunkingStrategy.FIXED_SIZE, 0, Instant.now());
        when(manifests.createFile(any())).thenReturn(expected);
        UploadService service = new UploadService(properties, new ChunkerFactory(properties), store, sessions,
                manifests, new StorageLifecycleLock());
        byte[] bytes = new byte[2048];

        UploadResponse actual = service.uploadNew("../../safe.bin", null, bytes.length,
                ChunkingStrategy.FIXED_SIZE, new ByteArrayInputStream(bytes));

        ArgumentCaptor<PreparedUpload> capture = ArgumentCaptor.forClass(PreparedUpload.class);
        verify(manifests).createFile(capture.capture());
        assertThat(capture.getValue().filename()).isEqualTo("safe.bin");
        assertThat(capture.getValue().chunks()).hasSize(2);
        assertThat(actual).isSameAs(expected);
        verify(sessions, times(2)).save(any(UploadSession.class));
    }
}
