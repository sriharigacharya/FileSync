package com.filesync.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class PcState {
    @Id
    private String syncGroupId; // one row per sync group
    private long currentStateVersion; // incremented every time new files are added

    public PcState() {}

    public PcState(String syncGroupId, long currentStateVersion) {
        this.syncGroupId = syncGroupId;
        this.currentStateVersion = currentStateVersion;
    }

    public String getSyncGroupId() { return syncGroupId; }
    public void setSyncGroupId(String syncGroupId) { this.syncGroupId = syncGroupId; }
    public long getCurrentStateVersion() { return currentStateVersion; }
    public void setCurrentStateVersion(long currentStateVersion) { this.currentStateVersion = currentStateVersion; }
}
