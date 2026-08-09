package com.filesync.controller;

import com.filesync.controller.dto.DiffRequest;
import com.filesync.controller.dto.SyncPlan;
import com.filesync.indexer.DiffEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/diff")
public class DiffController {

    @Autowired
    private DiffEngine diffEngine;

    /**
     * Computes the diff / sync plan for a sync group across specified connected devices.
     *
     * @param groupId Target sync group ID
     * @param request Payload containing deviceIds list (optional)
     * @return SyncPlan JSON object with files and conflicts arrays
     */
    @PostMapping
    public ResponseEntity<SyncPlan> computeDiff(
            @RequestParam String groupId,
            @RequestBody(required = false) DiffRequest request) {

        List<String> deviceIds = (request != null && request.getDeviceIds() != null)
                ? request.getDeviceIds()
                : Collections.emptyList();

        SyncPlan plan = diffEngine.computeDiff(groupId, deviceIds);
        return ResponseEntity.ok(plan);
    }
}
