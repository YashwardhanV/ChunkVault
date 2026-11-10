package com.chunkvault.exception;

public class UploadTooLargeException extends RuntimeException {
    public UploadTooLargeException(long maximumBytes) {
        super("Upload exceeds configured maximum of " + maximumBytes + " bytes");
    }
}
