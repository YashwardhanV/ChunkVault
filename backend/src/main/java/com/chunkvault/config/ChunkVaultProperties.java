package com.chunkvault.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "chunkvault")
public record ChunkVaultProperties(
        Path storageRoot,
        @Min(1) long maxUploadBytes,
        @Min(1024) int fixedChunkSize,
        @Min(1024) int cdcMinSize,
        @Min(1024) int cdcTargetSize,
        @Min(1024) int cdcMaxSize,
        Duration gcGracePeriod,
        boolean verifyOnReuse,
        @NotBlank String allowedOrigin
) {
    @AssertTrue(message = "CDC sizes must satisfy min <= target <= max and target must be a power of two")
    public boolean isCdcConfigurationValid() {
        return cdcMinSize <= cdcTargetSize
                && cdcTargetSize <= cdcMaxSize
                && (cdcTargetSize & (cdcTargetSize - 1)) == 0;
    }
}
