package com.chunkvault.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record VerifyRequest(@NotNull Scope scope, String chunkHash, UUID fileId, Integer versionNumber) {
    public enum Scope { ALL_CHUNKS, CHUNK, FILE }
}
