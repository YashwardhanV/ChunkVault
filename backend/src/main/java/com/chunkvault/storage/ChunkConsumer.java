package com.chunkvault.storage;

import java.io.IOException;

@FunctionalInterface
public interface ChunkConsumer {
    void accept(ChunkData chunk) throws IOException;
}
