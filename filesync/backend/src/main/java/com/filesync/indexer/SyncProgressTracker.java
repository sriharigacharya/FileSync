package com.filesync.indexer;

import com.filesync.controller.dto.SyncProgress;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class SyncProgressTracker {

    private final ConcurrentHashMap<String, SyncProgress> progressMap = new ConcurrentHashMap<>();

    public synchronized boolean startSync(String groupId, int totalFiles, long totalBytes) {
        SyncProgress existing = progressMap.get(groupId);
        if (existing != null && "IN_PROGRESS".equals(existing.getStatus())) {
            return false; // Sync already in progress for this group
        }

        SyncProgress progress = new SyncProgress(groupId, "IN_PROGRESS", "", 0, totalFiles, 0L, totalBytes, null);
        progressMap.put(groupId, progress);
        return true;
    }

    public void updateChunk(String groupId, String currentFile, long additionalBytes) {
        SyncProgress p = progressMap.get(groupId);
        if (p != null && "IN_PROGRESS".equals(p.getStatus())) {
            synchronized (p) {
                p.setCurrentFile(currentFile);
                p.setBytesCopied(p.getBytesCopied() + additionalBytes);
            }
        }
    }

    public void completeFile(String groupId) {
        SyncProgress p = progressMap.get(groupId);
        if (p != null && "IN_PROGRESS".equals(p.getStatus())) {
            synchronized (p) {
                p.setFilesCopied(p.getFilesCopied() + 1);
            }
        }
    }

    public void finishSync(String groupId) {
        SyncProgress p = progressMap.get(groupId);
        if (p != null) {
            synchronized (p) {
                p.setStatus("COMPLETED");
                p.setCurrentFile("");
                p.setBytesCopied(p.getTotalBytes());
            }
        }
    }

    public void failSync(String groupId, String errorMessage) {
        SyncProgress p = progressMap.get(groupId);
        if (p != null) {
            synchronized (p) {
                p.setStatus("FAILED");
                p.setErrorMessage(errorMessage);
            }
        }
    }

    public SyncProgress getProgress(String groupId) {
        SyncProgress p = progressMap.get(groupId);
        if (p == null) {
            return new SyncProgress(groupId, "IDLE", "", 0, 0, 0L, 0L, null);
        }
        synchronized (p) {
            return new SyncProgress(
                    p.getGroupId(),
                    p.getStatus(),
                    p.getCurrentFile(),
                    p.getFilesCopied(),
                    p.getTotalFiles(),
                    p.getBytesCopied(),
                    p.getTotalBytes(),
                    p.getErrorMessage()
            );
        }
    }
}
