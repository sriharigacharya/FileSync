package com.filesync.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;

@Entity
public class SyncGroup {
    @Id
    private String id; // UUID, generated on creation
    private String label; // e.g. "Goa Trip 2026"
    private Instant createdAt;

    public SyncGroup() {}

    public SyncGroup(String id, String label, Instant createdAt) {
        this.id = id;
        this.label = label;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
