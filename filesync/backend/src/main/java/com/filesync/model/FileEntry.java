package com.filesync.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import java.time.Instant;

@Entity
@IdClass(FileEntryId.class)
public class FileEntry {
    @Id
    private String hash; // SHA-256 hex digest
    @Id
    private String syncGroupId; // scopes entries to one sync group

    private String relativePath; // first-seen relative path
    private long size;
    private String firstSeenOnDeviceId;
    private Instant firstSeenAt;

    public FileEntry() {}

    public FileEntry(String hash, String syncGroupId, String relativePath, long size, String firstSeenOnDeviceId, Instant firstSeenAt) {
        this.hash = hash;
        this.syncGroupId = syncGroupId;
        this.relativePath = relativePath;
        this.size = size;
        this.firstSeenOnDeviceId = firstSeenOnDeviceId;
        this.firstSeenAt = firstSeenAt;
    }

    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
    public String getSyncGroupId() { return syncGroupId; }
    public void setSyncGroupId(String syncGroupId) { this.syncGroupId = syncGroupId; }
    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public String getFirstSeenOnDeviceId() { return firstSeenOnDeviceId; }
    public void setFirstSeenOnDeviceId(String firstSeenOnDeviceId) { this.firstSeenOnDeviceId = firstSeenOnDeviceId; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(Instant firstSeenAt) { this.firstSeenAt = firstSeenAt; }
}
