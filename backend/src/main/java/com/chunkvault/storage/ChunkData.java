package com.chunkvault.storage;

public record ChunkData(byte[] bytes, int size, int index, long offset, String hash) {
}
