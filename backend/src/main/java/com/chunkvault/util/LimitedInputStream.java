package com.chunkvault.util;

import com.chunkvault.exception.UploadTooLargeException;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class LimitedInputStream extends FilterInputStream {
    private final long maximum;
    private long count;

    public LimitedInputStream(InputStream input, long maximum) {
        super(input);
        this.maximum = maximum;
    }

    @Override
    public int read() throws IOException {
        int value = super.read();
        if (value >= 0) increment(1);
        return value;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        int read = super.read(bytes, offset, length);
        if (read > 0) increment(read);
        return read;
    }

    private void increment(int amount) {
        count += amount;
        if (count > maximum) {
            throw new UploadTooLargeException(maximum);
        }
    }

    public long count() {
        return count;
    }
}
