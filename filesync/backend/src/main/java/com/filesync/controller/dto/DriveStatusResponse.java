package com.filesync.controller.dto;

/**
 * Response DTO returned by {@code GET /api/devices?groupId=...}.
 *
 * <p>Each entry represents a currently connected drive annotated with its
 * recognition status:
 * <ul>
 *   <li>{@code RECOGNIZED} — a valid {@code .filesync/sync-meta.json} was found;
 *       the sync metadata fields are populated.</li>
 *   <li>{@code NEW} — no matching signature; the drive has not been assigned yet.</li>
 * </ul>
 *
 * <p>When a device is registered to a group but its drive is not currently mounted,
 * it is still included in the response with {@code connected = false} so the UI
 * can display an "Offline" badge.
 */
public class DriveStatusResponse {

    // --- Drive info (always present) ---
    private String drivePath;
    private String driveLabel;
    private long   totalBytes;
    private long   freeBytes;

    // --- Recognition status ---
    private String  status;  // "RECOGNIZED" or "NEW"
    private boolean connected = true; // false when device is registered but drive not mounted

    // --- Signature details (populated when status == RECOGNIZED) ---
    private String  syncRootPath;
    private String  syncGroupId;
    private String  syncGroupLabel;
    private String  deviceId;
    private String  deviceLabel;
    private boolean sourceOnly;
    private long    lastSyncedPcStateVersion;
    private long    currentPcStateVersion;

    public DriveStatusResponse() {}

    // --- Getters & setters ---

    public String getDrivePath()    { return drivePath; }
    public void setDrivePath(String drivePath) { this.drivePath = drivePath; }

    public String getDriveLabel()   { return driveLabel; }
    public void setDriveLabel(String driveLabel) { this.driveLabel = driveLabel; }

    public long getTotalBytes()     { return totalBytes; }
    public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }

    public long getFreeBytes()      { return freeBytes; }
    public void setFreeBytes(long freeBytes) { this.freeBytes = freeBytes; }

    public String getStatus()       { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSyncRootPath() { return syncRootPath; }
    public void setSyncRootPath(String syncRootPath) { this.syncRootPath = syncRootPath; }

    public String getSyncGroupId()  { return syncGroupId; }
    public void setSyncGroupId(String syncGroupId) { this.syncGroupId = syncGroupId; }

    public String getSyncGroupLabel() { return syncGroupLabel; }
    public void setSyncGroupLabel(String syncGroupLabel) { this.syncGroupLabel = syncGroupLabel; }

    public String getDeviceId()     { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getDeviceLabel()  { return deviceLabel; }
    public void setDeviceLabel(String deviceLabel) { this.deviceLabel = deviceLabel; }

    public boolean isSourceOnly()   { return sourceOnly; }
    public void setSourceOnly(boolean sourceOnly) { this.sourceOnly = sourceOnly; }

    public long getLastSyncedPcStateVersion() { return lastSyncedPcStateVersion; }
    public void setLastSyncedPcStateVersion(long lastSyncedPcStateVersion) { this.lastSyncedPcStateVersion = lastSyncedPcStateVersion; }

    public long getCurrentPcStateVersion() { return currentPcStateVersion; }
    public void setCurrentPcStateVersion(long currentPcStateVersion) { this.currentPcStateVersion = currentPcStateVersion; }

    public boolean isConnected()    { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }
}
