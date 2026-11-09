package com.chunkvault.dto;

import java.time.Instant;
import java.util.List;

public record VerifyResponse(long healthy, long missing, long corrupted, List<VerifyItem> items, Instant completedAt) {
    public enum Status { HEALTHY, MISSING, CORRUPTED }
    public record VerifyItem(String targetType, String target, Status status, String expectedHash,
                             String actualHash, long size, String message) { }
}
