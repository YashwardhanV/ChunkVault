package com.chunkvault.storage;

import com.chunkvault.util.Hashing;

import java.io.IOException;
import java.io.InputStream;

public final class FixedSizeChunker implements Chunker {
    static final int MAX_READ_SIZE = 64 * 1024;
    private final int chunkSize;

    public FixedSizeChunker(int chunkSize) {
        if (chunkSize <= 0) throw new IllegalArgumentException("chunkSize must be positive");
        this.chunkSize = chunkSize;
    }

    @Override
    public void split(InputStream input, ChunkConsumer consumer) throws IOException {
        int index = 0;
        long offset = 0;
        while (true) {
            byte[] chunk = new byte[chunkSize];
            int position = 0;
            while (position < chunkSize) {
                int read = input.read(chunk, position, Math.min(MAX_READ_SIZE, chunkSize - position));
                if (read == -1) break;
                if (read == 0) continue;
                position += read;
            }
            if (position == 0) return;
            consumer.accept(new ChunkData(chunk, position, index++, offset, Hashing.sha256(chunk, position)));
            offset += position;
            if (position < chunkSize) return;
        }
    }

    public int chunkSize() {
        return chunkSize;
    }
}
