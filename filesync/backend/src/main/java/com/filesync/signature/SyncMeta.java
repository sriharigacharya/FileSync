package com.filesync.signature;

public class SyncMeta {
    private String syncGroupId;
    private String syncGroupLabel;
    private String deviceId;
    private String deviceLabel;
    private String syncRootPath;
    private String createdAt;
    private long lastSyncedPcStateVersion;
    // Persisted so source-only status survives a reconnect without a DB query
    private boolean sourceOnly = false;

    public SyncMeta() {}

    public String getSyncGroupId() { return syncGroupId; }
    public void setSyncGroupId(String syncGroupId) { this.syncGroupId = syncGroupId; }

    public String getSyncGroupLabel() { return syncGroupLabel; }
    public void setSyncGroupLabel(String syncGroupLabel) { this.syncGroupLabel = syncGroupLabel; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getDeviceLabel() { return deviceLabel; }
    public void setDeviceLabel(String deviceLabel) { this.deviceLabel = deviceLabel; }

    public String getSyncRootPath() { return syncRootPath; }
    public void setSyncRootPath(String syncRootPath) { this.syncRootPath = syncRootPath; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public long getLastSyncedPcStateVersion() { return lastSyncedPcStateVersion; }
    public void setLastSyncedPcStateVersion(long lastSyncedPcStateVersion) { this.lastSyncedPcStateVersion = lastSyncedPcStateVersion; }

    public boolean isSourceOnly() { return sourceOnly; }
    public void setSourceOnly(boolean sourceOnly) { this.sourceOnly = sourceOnly; }
}
