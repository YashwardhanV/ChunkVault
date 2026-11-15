package com.chunkvault.storage;

import java.io.IOException;
import java.io.InputStream;

public interface Chunker {
    void split(InputStream input, ChunkConsumer consumer) throws IOException;
}
