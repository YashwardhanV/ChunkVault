package com.chunkvault.storage;

import com.chunkvault.config.ChunkVaultProperties;
import com.chunkvault.util.Hashing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ContentAddressableChunkStoreTest {
    @TempDir Path temp;

    @Test
    void generatesFanoutPathAndReusesCanonicalBytes() throws Exception {
        ContentAddressableChunkStore store = new ContentAddressableChunkStore(properties());
        store.initialize();
        byte[] bytes = "same chunk".getBytes(StandardCharsets.UTF_8);
        String hash = Hashing.sha256(bytes, bytes.length);

        StoredChunk first = store.store(UUID.randomUUID(), new ChunkData(bytes, bytes.length, 0, 0, hash));
        StoredChunk second = store.store(UUID.randomUUID(), new ChunkData(bytes, bytes.length, 1, 10, hash));

        assertThat(first.relativePath()).isEqualTo(hash.substring(0, 2) + "/" + hash.substring(2, 4) + "/" + hash);
        assertThat(second.relativePath()).isEqualTo(first.relativePath());
        assertThat(store.listCanonicalFiles()).hasSize(1);
    }

    private ChunkVaultProperties properties() {
        return new ChunkVaultProperties(temp, 1_000_000, 1024, 256, 512, 1024,
                Duration.ZERO, true, "http://localhost:3000");
    }
}
