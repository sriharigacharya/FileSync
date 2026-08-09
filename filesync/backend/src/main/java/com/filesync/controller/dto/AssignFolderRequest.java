package com.filesync.controller.dto;

/**
 * Request body for {@code POST /api/devices/{deviceId}/folder}.
 *
 * <p>Contains everything needed to bind a sync folder on a connected drive to
 * a sync group: the absolute folder path, the target group, a human-readable
 * label, and whether this device is source-only.
 */
public class AssignFolderRequest {
    /** Absolute path to the chosen folder on the drive, e.g. "D:\\Photos". */
    private String folderPath;

    /** ID of the sync group this drive should belong to. */
    private String syncGroupId;

    /** Human-readable label for this device, e.g. "Rahul's Pendrive". */
    private String deviceLabel;

    /** If true, device contributes files but is never a write target. */
    private boolean sourceOnly;

    public String getFolderPath()   { return folderPath; }
    public void setFolderPath(String folderPath) { this.folderPath = folderPath; }

    public String getSyncGroupId()  { return syncGroupId; }
    public void setSyncGroupId(String syncGroupId) { this.syncGroupId = syncGroupId; }

    public String getDeviceLabel()  { return deviceLabel; }
    public void setDeviceLabel(String deviceLabel) { this.deviceLabel = deviceLabel; }

    public boolean isSourceOnly()   { return sourceOnly; }
    public void setSourceOnly(boolean sourceOnly) { this.sourceOnly = sourceOnly; }
}
