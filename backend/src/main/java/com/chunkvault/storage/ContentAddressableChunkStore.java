package com.chunkvault.storage;

import com.chunkvault.config.ChunkVaultProperties;
import com.chunkvault.exception.IntegrityException;
import com.chunkvault.util.Hashing;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class ContentAddressableChunkStore {
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    private final Path root;
    private final Path tempRoot;
    private final boolean verifyOnReuse;

    public ContentAddressableChunkStore(ChunkVaultProperties properties) {
        root = properties.storageRoot().toAbsolutePath().normalize();
        tempRoot = root.resolve(".tmp");
        verifyOnReuse = properties.verifyOnReuse();
    }

    @PostConstruct
    public void initialize() throws IOException {
        Files.createDirectories(tempRoot);
    }

    public StoredChunk store(UUID sessionId, ChunkData chunk) throws IOException {
        validateHash(chunk.hash());
        Path sessionDirectory = tempRoot.resolve(sessionId.toString());
        Files.createDirectories(sessionDirectory);
        Path temporary = sessionDirectory.resolve(chunk.hash() + "." + UUID.randomUUID() + ".part");
        writeAndForce(temporary, chunk.bytes(), chunk.size());

        String relativePath = relativePath(chunk.hash());
        Path canonical = resolveRelative(relativePath);
        Files.createDirectories(canonical.getParent());
        publishNonReplacing(temporary, canonical);
        verifyExisting(canonical, chunk.hash(), chunk.size());
        return new StoredChunk(chunk.hash(), chunk.size(), relativePath, chunk.index(), chunk.offset());
    }

    private void writeAndForce(Path target, byte[] bytes, int length) throws IOException {
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes, 0, length);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    private void publishNonReplacing(Path temporary, Path canonical) throws IOException {
        try {
            Files.createLink(canonical, temporary);
            Files.deleteIfExists(temporary);
            return;
        } catch (FileAlreadyExistsException existing) {
            Files.deleteIfExists(temporary);
            return;
        } catch (UnsupportedOperationException | FileSystemException hardLinkUnavailable) {
            if (Files.exists(canonical)) {
                Files.deleteIfExists(temporary);
                return;
            }
        }

        try {
            Files.move(temporary, canonical, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            moveWithoutReplace(temporary, canonical);
        } catch (FileAlreadyExistsException existing) {
            Files.deleteIfExists(temporary);
        }
    }

    private void moveWithoutReplace(Path temporary, Path canonical) throws IOException {
        try {
            Files.move(temporary, canonical);
        } catch (FileAlreadyExistsException existing) {
            Files.deleteIfExists(temporary);
        }
    }

    private void verifyExisting(Path path, String expectedHash, long expectedSize) throws IOException {
        long actualSize = Files.size(path);
        if (actualSize != expectedSize) {
            throw new IntegrityException("Hash collision or damaged chunk: expected " + expectedSize
                    + " bytes at " + expectedHash + " but found " + actualSize);
        }
        if (verifyOnReuse) {
            String actualHash = Hashing.sha256(path);
            if (!expectedHash.equals(actualHash)) {
                throw new IntegrityException("Canonical chunk content does not match address " + expectedHash);
            }
        }
    }

    public Path resolveRelative(String relativePath) {
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) throw new IllegalArgumentException("Storage path escapes root");
        return resolved;
    }

    public Path pathForHash(String hash) {
        validateHash(hash);
        return resolveRelative(relativePath(hash));
    }

    public String relativePath(String hash) {
        validateHash(hash);
        return hash.substring(0, 2) + "/" + hash.substring(2, 4) + "/" + hash;
    }

    public void cleanupSession(UUID sessionId) {
        deleteTree(tempRoot.resolve(sessionId.toString()));
    }

    public int cleanupStaleTemporaryDirectories(Instant cutoff) {
        if (!Files.isDirectory(tempRoot)) return 0;
        int removed = 0;
        try (Stream<Path> children = Files.list(tempRoot)) {
            for (Path child : children.toList()) {
                try {
                    FileTime modified = Files.getLastModifiedTime(child);
                    if (modified.toInstant().isBefore(cutoff)) {
                        deleteTree(child);
                        removed++;
                    }
                } catch (IOException ignored) {
                    // Counted by the caller only for canonical deletion failures.
                }
            }
        } catch (IOException ignored) {
            return removed;
        }
        return removed;
    }

    public List<Path> listCanonicalFiles() throws IOException {
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> files = Files.walk(root, 3)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> HASH.matcher(path.getFileName().toString()).matches())
                    .toList();
        }
    }

    private void deleteTree(Path directory) {
        if (!directory.normalize().startsWith(tempRoot) || !Files.exists(directory)) return;
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) {
            // Cleanup is best effort; stale-temp GC retries later.
        }
    }

    private static void validateHash(String hash) {
        if (hash == null || !HASH.matcher(hash).matches()) {
            throw new IllegalArgumentException("Expected lowercase SHA-256 hash");
        }
    }
}
