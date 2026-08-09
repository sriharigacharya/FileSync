package com.filesync.controller.dto;

public class SyncProgress {
    private String groupId;
    private String status; // "IDLE", "IN_PROGRESS", "COMPLETED", "FAILED"
    private String currentFile;
    private int filesCopied;
    private int totalFiles;
    private long bytesCopied;
    private long totalBytes;
    private double percent;
    private String errorMessage;

    public SyncProgress() {
        this.status = "IDLE";
    }

    public SyncProgress(String groupId, String status, String currentFile, int filesCopied, int totalFiles, long bytesCopied, long totalBytes, String errorMessage) {
        this.groupId = groupId;
        this.status = status;
        this.currentFile = currentFile;
        this.filesCopied = filesCopied;
        this.totalFiles = totalFiles;
        this.bytesCopied = bytesCopied;
        this.totalBytes = totalBytes;
        this.percent = totalBytes > 0 ? Math.min(100.0, (bytesCopied * 100.0 / totalBytes)) : 0.0;
        this.errorMessage = errorMessage;
    }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCurrentFile() { return currentFile; }
    public void setCurrentFile(String currentFile) { this.currentFile = currentFile; }

    public int getFilesCopied() { return filesCopied; }
    public void setFilesCopied(int filesCopied) { this.filesCopied = filesCopied; }

    public int getTotalFiles() { return totalFiles; }
    public void setTotalFiles(int totalFiles) { this.totalFiles = totalFiles; }

    public long getBytesCopied() { return bytesCopied; }
    public void setBytesCopied(long bytesCopied) {
        this.bytesCopied = bytesCopied;
        this.percent = totalBytes > 0 ? Math.min(100.0, (bytesCopied * 100.0 / totalBytes)) : 0.0;
    }

    public long getTotalBytes() { return totalBytes; }
    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
        this.percent = totalBytes > 0 ? Math.min(100.0, (bytesCopied * 100.0 / totalBytes)) : 0.0;
    }

    public double getPercent() { return percent; }
    public void setPercent(double percent) { this.percent = percent; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
