package com.filesync.controller.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SyncPlan {

    private List<FileDiff> files = new ArrayList<>();
    private List<ConflictDiff> conflicts = new ArrayList<>();

    public SyncPlan() {}

    public SyncPlan(List<FileDiff> files, List<ConflictDiff> conflicts) {
        this.files = files;
        this.conflicts = conflicts;
    }

    public List<FileDiff> getFiles() { return files; }
    public void setFiles(List<FileDiff> files) { this.files = files; }

    public List<ConflictDiff> getConflicts() { return conflicts; }
    public void setConflicts(List<ConflictDiff> conflicts) { this.conflicts = conflicts; }

    public static class FileDiff {
        private String hash;
        private String relativePath;
        private List<String> presentOn = new ArrayList<>();
        private List<String> missingOn = new ArrayList<>();

        public FileDiff() {}

        public FileDiff(String hash, String relativePath, List<String> presentOn, List<String> missingOn) {
            this.hash = hash;
            this.relativePath = relativePath;
            this.presentOn = presentOn;
            this.missingOn = missingOn;
        }

        public String getHash() { return hash; }
        public void setHash(String hash) { this.hash = hash; }

        public String getRelativePath() { return relativePath; }
        public void setRelativePath(String relativePath) { this.relativePath = relativePath; }

        public List<String> getPresentOn() { return presentOn; }
        public void setPresentOn(List<String> presentOn) { this.presentOn = presentOn; }

        public List<String> getMissingOn() { return missingOn; }
        public void setMissingOn(List<String> missingOn) { this.missingOn = missingOn; }
    }

    public static class ConflictDiff {
        private String relativePath;
        private Map<String, String> devices;

        public ConflictDiff() {}

        public ConflictDiff(String relativePath, Map<String, String> devices) {
            this.relativePath = relativePath;
            this.devices = devices;
        }

        public String getRelativePath() { return relativePath; }
        public void setRelativePath(String relativePath) { this.relativePath = relativePath; }

        public Map<String, String> getDevices() { return devices; }
        public void setDevices(Map<String, String> devices) { this.devices = devices; }
    }
}
