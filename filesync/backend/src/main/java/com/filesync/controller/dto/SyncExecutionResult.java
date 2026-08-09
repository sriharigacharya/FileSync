package com.filesync.controller.dto;

public class SyncExecutionResult {
    private int copiedCount;
    private int skippedCount;
    private long pcStateVersion;
    private String status;

    public SyncExecutionResult() {}

    public SyncExecutionResult(int copiedCount, int skippedCount, long pcStateVersion, String status) {
        this.copiedCount = copiedCount;
        this.skippedCount = skippedCount;
        this.pcStateVersion = pcStateVersion;
        this.status = status;
    }

    public int getCopiedCount() { return copiedCount; }
    public void setCopiedCount(int copiedCount) { this.copiedCount = copiedCount; }

    public int getSkippedCount() { return skippedCount; }
    public void setSkippedCount(int skippedCount) { this.skippedCount = skippedCount; }

    public long getPcStateVersion() { return pcStateVersion; }
    public void setPcStateVersion(long pcStateVersion) { this.pcStateVersion = pcStateVersion; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
