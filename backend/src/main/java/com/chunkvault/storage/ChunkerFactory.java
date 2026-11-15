package com.chunkvault.storage;

import com.chunkvault.config.ChunkVaultProperties;
import com.chunkvault.domain.ChunkingStrategy;
import org.springframework.stereotype.Component;

@Component
public class ChunkerFactory {
    private final FixedSizeChunker fixed;
    private final GearContentDefinedChunker contentDefined;

    public ChunkerFactory(ChunkVaultProperties properties) {
        fixed = new FixedSizeChunker(properties.fixedChunkSize());
        contentDefined = new GearContentDefinedChunker(
                properties.cdcMinSize(), properties.cdcTargetSize(), properties.cdcMaxSize());
    }

    public Chunker forStrategy(ChunkingStrategy strategy) {
        return strategy == ChunkingStrategy.CONTENT_DEFINED ? contentDefined : fixed;
    }
}
