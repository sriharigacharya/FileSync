package com.filesync.drive;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lists the immediate sub-folders of a given path for the folder-picker UI.
 *
 * <p>Only directories are returned; files are excluded.  Hidden directories
 * (name starts with {@code .}) and known system directories are filtered out so
 * the picker shows only user-meaningful folders.
 *
 * <p>Results are sorted alphabetically (case-insensitive) for consistent
 * display across platforms.
 */
@Service
public class FolderBrowser {

    /** Max number of entries returned to keep response sizes sane. */
    private static final int MAX_ENTRIES = 200;

    /**
     * Returns the names and absolute paths of immediate sub-directories of
     * {@code parentPath}.
     *
     * @param parentPath absolute path to list
     * @return sorted list of {@link FolderEntry}; empty if the path is not a
     *         readable directory or has no sub-directories
     */
    public List<FolderEntry> listSubfolders(String parentPath) {
        Path parent = Paths.get(parentPath);
        if (!Files.isDirectory(parent) || !Files.isReadable(parent)) {
            return Collections.emptyList();
        }

        List<FolderEntry> entries = new ArrayList<>();
        try (var stream = Files.list(parent)) {
            stream
                .filter(Files::isDirectory)
                .filter(p -> {
                    String name = p.getFileName().toString();
                    // Skip hidden dirs and known Windows system dirs
                    return !name.startsWith(".")
                        && !name.startsWith("$")
                        && !name.equals("System Volume Information")
                        && !name.equals("Recovery")
                        && !name.equals("boot");
                })
                .limit(MAX_ENTRIES)
                .sorted((a, b) -> a.getFileName().toString()
                                   .compareToIgnoreCase(b.getFileName().toString()))
                .forEach(p -> entries.add(
                    new FolderEntry(p.getFileName().toString(),
                                    p.toAbsolutePath().toString())
                ));
        } catch (IOException ignored) {
            // Return whatever was collected before the error
        }

        return entries;
    }

    /** Simple DTO for a single folder entry. */
    public record FolderEntry(String name, String path) {}
}
