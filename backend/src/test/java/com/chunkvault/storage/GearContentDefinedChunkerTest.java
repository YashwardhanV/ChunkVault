package com.chunkvault.storage;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class GearContentDefinedChunkerTest {
    @Test
    void respectsMinimumAndMaximumChunkSizes() throws IOException {
        byte[] bytes = new byte[20_000];
        new Random(42).nextBytes(bytes);
        List<ChunkData> chunks = split(bytes);

        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.size()).isLessThanOrEqualTo(1024));
        assertThat(chunks.subList(0, chunks.size() - 1)).allSatisfy(chunk -> assertThat(chunk.size()).isGreaterThanOrEqualTo(256));
        assertThat(chunks.stream().mapToInt(ChunkData::size).sum()).isEqualTo(bytes.length);
    }

    @Test
    void resynchronizesAfterSmallInsertion() throws IOException {
        byte[] original = new byte[60_000];
        new Random(7).nextBytes(original);
        byte[] modified = new byte[original.length + 17];
        System.arraycopy(original, 0, modified, 0, 3_000);
        Arrays.fill(modified, 3_000, 3_017, (byte) 0x5a);
        System.arraycopy(original, 3_000, modified, 3_017, original.length - 3_000);

        Set<String> originalHashes = split(original).stream().map(ChunkData::hash).collect(Collectors.toSet());
        long reused = split(modified).stream().filter(chunk -> originalHashes.contains(chunk.hash())).count();

        assertThat(reused).isGreaterThan(10);
    }

    private List<ChunkData> split(byte[] bytes) throws IOException {
        List<ChunkData> chunks = new ArrayList<>();
        new GearContentDefinedChunker(256, 512, 1024).split(new ByteArrayInputStream(bytes), chunks::add);
        return chunks;
    }
}
