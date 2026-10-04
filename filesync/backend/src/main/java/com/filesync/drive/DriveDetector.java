package com.filesync.drive;

import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.List;

/**
 * Enumerates currently mounted drives / removable volumes visible to the JVM.
 *
 * Strategy:
 *  - On all platforms we iterate {@link FileSystems#getDefault()#getFileStores()} to
 *    get real mount points with capacity information.
 *  - We also cross-reference {@link File#listRoots()} (works on Windows to get
 *    drive letters A:–Z:) so that unreadable stores are still surfaced with a
 *    degraded entry.
 *
 * No native / JNI calls are made; this works on Windows, macOS, and Linux without
 * additional dependencies.
 */
@Service
public class DriveDetector {

    /**
     * Returns one {@link DriveInfo} per currently mounted drive/volume.
     * Entries are never null; individual fields may be -1 if the OS denied access.
     */
    public List<DriveInfo> listDrives() {
        List<DriveInfo> result = new ArrayList<>();

        try {
            for (FileStore store : FileSystems.getDefault().getFileStores()) {
                String path  = resolvePath(store);
                if (isCDriveOrSystemDrive(path)) {
                    continue;
                }
                String label = store.name().isBlank() ? store.type() : store.name();
                long   total = -1;
                long   free  = -1;

                try {
                    total = store.getTotalSpace();
                    free  = store.getUsableSpace();
                } catch (Exception ignored) {
                    // Some virtual/system stores throw — just leave -1
                }

                // Skip zero-capacity pseudo-stores (e.g., /proc, devtmpfs on Linux)
                // but keep entries whose size we couldn't read (total == -1).
                if (total == 0) {
                    continue;
                }

                result.add(new DriveInfo(path, label, total, free));
            }
        } catch (Exception e) {
            // Fallback: at least return root entries from File.listRoots()
            result.clear();
            File[] roots = File.listRoots();
            if (roots != null) {
                for (File root : roots) {
                    if (isCDriveOrSystemDrive(root.getAbsolutePath())) {
                        continue;
                    }
                    result.add(new DriveInfo(
                            root.getAbsolutePath(),
                            "",
                            root.getTotalSpace(),
                            root.getUsableSpace()
                    ));
                }
            }
        }

        return result;
    }

    /**
     * Checks if a path belongs to the primary C: system drive on Windows.
     * C: drive is reserved for the host OS and is never surfaced as a removable/external drive.
     */
    private boolean isCDriveOrSystemDrive(String path) {
        if (path == null || path.isBlank()) return false;
        String clean = path.replaceAll("[\\\\/]+$", "").toUpperCase();
        if ("C:".equals(clean)) {
            return true;
        }
        String sysDrive = System.getenv("SystemDrive");
        if (sysDrive != null && clean.equalsIgnoreCase(sysDrive.replaceAll("[\\\\/]+$", ""))) {
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Derives a readable path string from a {@link FileStore}.
     *
     * {@code FileStore.toString()} on the OpenJDK implementation returns something
     * like {@code "C:\ (Local Disk)"} on Windows or {@code "/dev/sda1 (/home)"} on
     * Linux.  We extract the bracketed mount point when present, otherwise fall back
     * to the raw toString().
     */
    private String resolvePath(FileStore store) {
        String raw = store.toString();
        // Linux/macOS: "name (mountpoint)"
        int open  = raw.lastIndexOf('(');
        int close = raw.lastIndexOf(')');
        if (open >= 0 && close > open) {
            return raw.substring(open + 1, close).trim();
        }
        // Windows: "C:\ (Volume Label)"  — take the part before the first '('
        if (open >= 0) {
            return raw.substring(0, open).trim();
        }
        return raw.trim();
    }
}
