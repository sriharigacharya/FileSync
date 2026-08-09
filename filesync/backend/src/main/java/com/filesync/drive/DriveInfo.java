package com.filesync.drive;

/**
 * Represents a single mounted drive / volume as returned by {@link DriveDetector}.
 *
 * @param path       Mount point or drive root, e.g. {@code "E:\"} or {@code "/media/usb0"}
 * @param label      Volume label or file-system type if the label is blank
 * @param totalBytes Total capacity in bytes; -1 if the OS denied access
 * @param freeBytes  Usable free space in bytes; -1 if the OS denied access
 */
public record DriveInfo(
        String path,
        String label,
        long   totalBytes,
        long   freeBytes
) {}
