package com.filesync.indexer;

public class ScannedFile {
    private String relativePath;
    private long size;
    private String hash;

    public ScannedFile() {}

    public ScannedFile(String relativePath, long size, String hash) {
        this.relativePath = relativePath;
        this.size = size;
        this.hash = hash;
    }

    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
}
