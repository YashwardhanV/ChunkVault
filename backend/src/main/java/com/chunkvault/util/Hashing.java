package com.chunkvault.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Hashing {
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final HexFormat HEX = HexFormat.of();

    private Hashing() {
    }

    public static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Required SHA-256 provider is unavailable", e);
        }
    }

    public static String sha256(byte[] bytes, int length) {
        MessageDigest digest = sha256Digest();
        digest.update(bytes, 0, length);
        return HEX.formatHex(digest.digest());
    }

    public static String sha256(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = sha256Digest();
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HEX.formatHex(digest.digest());
        }
    }

    public static String hex(byte[] digest) {
        return HEX.formatHex(digest);
    }
}
