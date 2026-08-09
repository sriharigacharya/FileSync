package com.filesync.controller;

import com.filesync.controller.dto.CreateGroupRequest;
import com.filesync.controller.dto.SetPcFolderRequest;
import com.filesync.model.Device;
import com.filesync.model.PcState;
import com.filesync.model.SyncGroup;
import com.filesync.repository.DeviceRepository;
import com.filesync.repository.FileEntryRepository;
import com.filesync.repository.PcStateRepository;
import com.filesync.repository.SyncGroupRepository;
import com.filesync.signature.SignatureFileManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    @Autowired
    private SyncGroupRepository syncGroupRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private PcStateRepository pcStateRepository;

    @Autowired
    private FileEntryRepository fileEntryRepository;

    @Autowired
    private SignatureFileManager signatureFileManager;

    @PostMapping
    public ResponseEntity<SyncGroup> createGroup(@RequestBody CreateGroupRequest request) {
        SyncGroup group = new SyncGroup(
                UUID.randomUUID().toString(),
                request.getLabel(),
                Instant.now()
        );
        SyncGroup saved = syncGroupRepository.save(group);
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public ResponseEntity<List<SyncGroup>> listGroups() {
        return ResponseEntity.ok(syncGroupRepository.findAll());
    }

    /**
     * Deletes a sync group and all associated DB records (Device, FileEntry, PcState).
     * Physical files on drives are never touched — signature files on pendrives remain.
     */
    @DeleteMapping("/{groupId}")
    @Transactional
    public ResponseEntity<?> deleteGroup(@PathVariable String groupId) {
        if (!syncGroupRepository.existsById(groupId)) {
            return ResponseEntity.notFound().build();
        }
        // Cascade: file entries → devices → pc state → group
        fileEntryRepository.deleteAllBySyncGroupId(groupId);
        deviceRepository.deleteAllBySyncGroupId(groupId);
        pcStateRepository.deleteById(groupId);
        syncGroupRepository.deleteById(groupId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/pc-folder")
    @Transactional
    public ResponseEntity<?> setPcFolder(@PathVariable String groupId, @RequestBody SetPcFolderRequest request) {
        SyncGroup group = syncGroupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        Path folderPath = Paths.get(request.getPath());

        // Initialize PcState if not exists
        PcState pcState = pcStateRepository.findById(groupId).orElseGet(() -> {
            PcState state = new PcState(groupId, 0L);
            return pcStateRepository.save(state);
        });

        // Find or create PC Device
        // We'll use a fixed device label for the PC
        String pcDeviceId;
        List<Device> devices = deviceRepository.findBySyncGroupId(groupId);
        Device pcDevice = devices.stream()
                .filter(d -> "PC".equals(d.getLabel()))
                .findFirst()
                .orElse(null);

        if (pcDevice == null) {
            pcDeviceId = UUID.randomUUID().toString();
            pcDevice = new Device(
                    pcDeviceId,
                    "PC",
                    folderPath.getRoot() != null ? folderPath.getRoot().toString() : "",
                    folderPath.toAbsolutePath().toString(),
                    groupId,
                    pcState.getCurrentStateVersion(),
                    Instant.now()
            );
            deviceRepository.save(pcDevice);
        } else {
            pcDeviceId = pcDevice.getId();
            pcDevice.setSyncRootPath(folderPath.toAbsolutePath().toString());
            pcDevice.setMountPath(folderPath.getRoot() != null ? folderPath.getRoot().toString() : "");
            deviceRepository.save(pcDevice);
        }

        // Write signature
        try {
            signatureFileManager.writeSignature(
                    folderPath,
                    groupId,
                    group.getLabel(),
                    pcDeviceId,
                    pcDevice.getLabel(),
                    pcState.getCurrentStateVersion()
            );
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Failed to write signature: " + e.getMessage());
        }

        return ResponseEntity.ok().build();
    }
}
