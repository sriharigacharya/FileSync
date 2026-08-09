package com.filesync.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;

@Entity
public class Device {
    @Id
    private String id; // deviceId
    private String label; // e.g. "Rahul's Pendrive"
    private String mountPath; // current session's drive letter/mount point, e.g. "E:\"
    private String syncRootPath; // absolute path to the chosen folder
    private String syncGroupId; // FK to SyncGroup
    private long lastSyncedPcStateVersion;
    private Instant lastSeen;
    // When true: device contributes its files to the sync group's accumulated
    // file set but is NEVER a write target — excluded from missingOn lists and
    // sync execution entirely.
    private boolean sourceOnly = false;

    public Device() {}

    public Device(String id, String label, String mountPath, String syncRootPath, String syncGroupId, long lastSyncedPcStateVersion, Instant lastSeen) {
        this(id, label, mountPath, syncRootPath, syncGroupId, lastSyncedPcStateVersion, lastSeen, false);
    }

    public Device(String id, String label, String mountPath, String syncRootPath, String syncGroupId, long lastSyncedPcStateVersion, Instant lastSeen, boolean sourceOnly) {
        this.id = id;
        this.label = label;
        this.mountPath = mountPath;
        this.syncRootPath = syncRootPath;
        this.syncGroupId = syncGroupId;
        this.lastSyncedPcStateVersion = lastSyncedPcStateVersion;
        this.lastSeen = lastSeen;
        this.sourceOnly = sourceOnly;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getMountPath() { return mountPath; }
    public void setMountPath(String mountPath) { this.mountPath = mountPath; }
    public String getSyncRootPath() { return syncRootPath; }
    public void setSyncRootPath(String syncRootPath) { this.syncRootPath = syncRootPath; }
    public String getSyncGroupId() { return syncGroupId; }
    public void setSyncGroupId(String syncGroupId) { this.syncGroupId = syncGroupId; }
    public long getLastSyncedPcStateVersion() { return lastSyncedPcStateVersion; }
    public void setLastSyncedPcStateVersion(long lastSyncedPcStateVersion) { this.lastSyncedPcStateVersion = lastSyncedPcStateVersion; }
    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }
    public boolean isSourceOnly() { return sourceOnly; }
    public void setSourceOnly(boolean sourceOnly) { this.sourceOnly = sourceOnly; }
}
