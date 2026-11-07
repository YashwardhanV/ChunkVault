package com.chunkvault.dto;

public record ChunkManifestItem(int index, long offset, int size, String hash, long referenceCount, boolean shared) {
}
