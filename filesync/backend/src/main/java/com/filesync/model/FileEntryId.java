package com.filesync.model;

import java.io.Serializable;
import java.util.Objects;

public class FileEntryId implements Serializable {
    private String hash;
    private String syncGroupId;

    public FileEntryId() {}

    public FileEntryId(String hash, String syncGroupId) {
        this.hash = hash;
        this.syncGroupId = syncGroupId;
    }

    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
    public String getSyncGroupId() { return syncGroupId; }
    public void setSyncGroupId(String syncGroupId) { this.syncGroupId = syncGroupId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileEntryId that = (FileEntryId) o;
        return Objects.equals(hash, that.hash) && Objects.equals(syncGroupId, that.syncGroupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hash, syncGroupId);
    }
}
