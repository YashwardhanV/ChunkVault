package com.chunkvault.storage;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FixedSizeChunkerTest {
    @Test
    void splitsWithCorrectIndexesOffsetsAndTail() throws IOException {
        FixedSizeChunker chunker = new FixedSizeChunker(4);
        List<ChunkData> chunks = new ArrayList<>();
        chunker.split(new java.io.ByteArrayInputStream("abcdefghij".getBytes()), chunks::add);

        assertThat(chunks).hasSize(3);
        assertThat(chunks).extracting(ChunkData::size).containsExactly(4, 4, 2);
        assertThat(chunks).extracting(ChunkData::index).containsExactly(0, 1, 2);
        assertThat(chunks).extracting(ChunkData::offset).containsExactly(0L, 4L, 8L);
    }

    @Test
    void requestsBoundedReadsForLargeStreams() throws IOException {
        TrackingGeneratedInputStream input = new TrackingGeneratedInputStream(64L * 1024 * 1024);
        FixedSizeChunker chunker = new FixedSizeChunker(4 * 1024 * 1024);
        int[] chunks = {0};

        chunker.split(input, ignored -> chunks[0]++);

        assertThat(chunks[0]).isEqualTo(16);
        assertThat(input.maximumRequested).isLessThanOrEqualTo(64 * 1024);
    }

    private static final class TrackingGeneratedInputStream extends InputStream {
        private long remaining;
        private int maximumRequested;

        private TrackingGeneratedInputStream(long remaining) { this.remaining = remaining; }

        @Override public int read() { if (remaining == 0) return -1; remaining--; return (int) (remaining & 0xff); }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            maximumRequested = Math.max(maximumRequested, length);
            if (remaining == 0) return -1;
            int count = (int) Math.min(length, remaining);
            for (int i = 0; i < count; i++) bytes[offset + i] = (byte) ((remaining - i) & 0xff);
            remaining -= count;
            return count;
        }
    }
}
