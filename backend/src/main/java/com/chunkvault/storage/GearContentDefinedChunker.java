package com.chunkvault.storage;

import com.chunkvault.util.Hashing;

import java.io.IOException;
import java.io.InputStream;

public final class GearContentDefinedChunker implements Chunker {
    private static final int READ_BUFFER_SIZE = 64 * 1024;
    private static final long[] GEAR = createGearTable();

    private final int minimumSize;
    private final int targetSize;
    private final int maximumSize;
    private final long boundaryMask;

    public GearContentDefinedChunker(int minimumSize, int targetSize, int maximumSize) {
        if (minimumSize <= 0 || minimumSize > targetSize || targetSize > maximumSize) {
            throw new IllegalArgumentException("Expected 0 < minimum <= target <= maximum");
        }
        if ((targetSize & (targetSize - 1)) != 0) {
            throw new IllegalArgumentException("targetSize must be a power of two");
        }
        this.minimumSize = minimumSize;
        this.targetSize = targetSize;
        this.maximumSize = maximumSize;
        this.boundaryMask = targetSize - 1L;
    }

    @Override
    public void split(InputStream input, ChunkConsumer consumer) throws IOException {
        byte[] readBuffer = new byte[READ_BUFFER_SIZE];
        byte[] chunk = new byte[maximumSize];
        int chunkLength = 0;
        int index = 0;
        long offset = 0;
        long fingerprint = 0;
        int read;
        while ((read = input.read(readBuffer)) != -1) {
            for (int i = 0; i < read; i++) {
                byte value = readBuffer[i];
                chunk[chunkLength++] = value;
                fingerprint = (fingerprint << 1) + GEAR[value & 0xff];
                boolean boundary = chunkLength >= maximumSize
                        || (chunkLength >= minimumSize && (fingerprint & boundaryMask) == 0);
                if (boundary) {
                    consumer.accept(new ChunkData(chunk, chunkLength, index++, offset, Hashing.sha256(chunk, chunkLength)));
                    offset += chunkLength;
                    chunk = new byte[maximumSize];
                    chunkLength = 0;
                    fingerprint = 0;
                }
            }
        }
        if (chunkLength > 0) {
            consumer.accept(new ChunkData(chunk, chunkLength, index, offset, Hashing.sha256(chunk, chunkLength)));
        }
    }

    private static long[] createGearTable() {
        long[] table = new long[256];
        long state = 0x4d595df4d0f33173L;
        for (int i = 0; i < table.length; i++) {
            state += 0x9e3779b97f4a7c15L;
            long value = state;
            value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
            value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
            table[i] = value ^ (value >>> 31);
        }
        return table;
    }

    public int minimumSize() { return minimumSize; }
    public int targetSize() { return targetSize; }
    public int maximumSize() { return maximumSize; }
}
