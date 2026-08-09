package com.filesync.controller;

import com.filesync.controller.dto.SyncExecutionRequest;
import com.filesync.controller.dto.SyncExecutionResult;
import com.filesync.controller.dto.SyncProgress;
import com.filesync.indexer.SyncExecutor;
import com.filesync.indexer.SyncProgressTracker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    @Autowired
    private SyncExecutor syncExecutor;

    @Autowired
    private SyncProgressTracker syncProgressTracker;

    /**
     * Executes the copy plan for approved file hashes across participating devices.
     *
     * @param groupId Target sync group ID
     * @param request Payload containing approvedHashes and deviceIds list
     * @return SyncExecutionResult JSON object
     */
    @PostMapping
    public ResponseEntity<?> executeSync(
            @RequestParam String groupId,
            @RequestBody SyncExecutionRequest request) {

        try {
            SyncExecutionResult result = syncExecutor.executeSync(
                    groupId,
                    request.getApprovedHashes(),
                    request.getDeviceIds()
            );
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    /**
     * Returns current sync progress for a sync group.
     *
     * @param groupId Target sync group ID
     * @return SyncProgress JSON object
     */
    @GetMapping("/progress")
    public ResponseEntity<SyncProgress> getSyncProgress(@RequestParam String groupId) {
        SyncProgress progress = syncProgressTracker.getProgress(groupId);
        return ResponseEntity.ok(progress);
    }
}
