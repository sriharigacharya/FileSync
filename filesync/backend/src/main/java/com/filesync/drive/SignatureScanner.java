package com.filesync.drive;

import com.filesync.signature.SignatureFileManager;
import com.filesync.signature.SyncMeta;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Performs a bounded shallow scan of a drive root looking for
 * {@code .filesync/sync-meta.json} signature files.
 *
 * <p>The scan is intentionally shallow: it descends at most {@link #MAX_DEPTH}
 * directory levels from the drive root so we never block on large directory trees.
 * At each directory it checks whether {@code <dir>/.filesync/sync-meta.json}
 * exists and can be parsed; when it finds one it stops descending that subtree.
 *
 * <p>Rationale for depth 3: a user might place their sync folder one or two
 * levels below the root (e.g. {@code D:\Photos} or {@code D:\Work\Trips\2026}).
 * We don't want to walk the whole drive, but we do want to catch reasonable
 * user-chosen locations.
 */
@Service
public class SignatureScanner {

    /** Maximum directory depth to descend from the drive root (inclusive). */
    public static final int MAX_DEPTH = 3;

    @Autowired
    private SignatureFileManager signatureFileManager;

    /**
     * Scans {@code driveRoot} up to {@link #MAX_DEPTH} levels deep and returns
     * every {@link SyncMeta} found.  In practice this will usually return 0 or 1
     * results; returning a list handles the (unusual) case where a drive has
     * multiple sync folders from different groups.
     *
     * @param driveRoot the root path to scan, e.g. {@code Path.of("D:\\")}
     * @return list of all {@link SyncMeta} objects found (never null, may be empty)
     */
    public List<SyncMeta> scanForSignatures(Path driveRoot) {
        List<SyncMeta> found = new ArrayList<>();

        if (!Files.exists(driveRoot) || !Files.isReadable(driveRoot)) {
            return found;
        }

        try {
            Files.walkFileTree(driveRoot, new SimpleFileVisitor<Path>() {

                // Track depth manually so we can stop early.
                // walkFileTree depth = number of path components below root.
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    // Skip hidden system directories that are never user sync roots
                    String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (name.startsWith("$") || name.equals("System Volume Information")
                            || name.equals("Recovery") || name.equals("boot")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    int depth = driveRoot.relativize(dir).getNameCount();
                    if (depth > MAX_DEPTH) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    // Check if this directory itself has a .filesync signature
                    Optional<SyncMeta> meta = signatureFileManager.readSignature(dir);
                    if (meta.isPresent()) {
                        found.add(meta.get());
                        // Don't descend — a sync folder won't contain another sync folder
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    // Silently skip inaccessible files / directories
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // Best-effort — return whatever was found so far
        }

        return found;
    }
}
