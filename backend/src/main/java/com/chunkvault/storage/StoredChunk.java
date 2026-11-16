package com.chunkvault.storage;

public record StoredChunk(String hash, int size, String relativePath, int index, long offset) {
}
