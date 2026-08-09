package com.filesync.controller;

import com.filesync.controller.dto.AssignFolderRequest;
import com.filesync.controller.dto.DriveStatusResponse;
import com.filesync.drive.DriveDetector;
import com.filesync.drive.DriveInfo;
import com.filesync.drive.FolderBrowser;
import com.filesync.drive.SignatureScanner;
import com.filesync.model.Device;
import com.filesync.model.PcState;
import com.filesync.model.SyncGroup;
import com.filesync.repository.DeviceRepository;
import com.filesync.repository.PcStateRepository;
import com.filesync.repository.SyncGroupRepository;
import com.filesync.signature.SignatureFileManager;
import com.filesync.signature.SyncMeta;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;

/**
 * REST controller exposing device / drive-related endpoints.
 *
 * <p>Phase 5 endpoints:
 * <ul>
 *   <li>{@code GET /api/devices?groupId=}  — connected drives annotated as
 *       RECOGNIZED or NEW</li>
 *   <li>{@code GET /api/drives}            — raw mounted drive list</li>
 *   <li>{@code GET /api/browse?path=}      — folder picker</li>
 *   <li>{@code POST /api/devices/{deviceId}/folder} — assign a sync folder
 *       to a drive</li>
 * </ul>
 */
@RestController
public class DeviceController {

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private SyncGroupRepository syncGroupRepository;

    @Autowired
    private DriveDetector driveDetector;

    @Autowired
    private SignatureScanner signatureScanner;

    @Autowired
    private FolderBrowser folderBrowser;

    @Autowired
    private SignatureFileManager signatureFileManager;

    @Autowired
    private PcStateRepository pcStateRepository;

    // ------------------------------------------------------------------
    // GET /api/devices?groupId=...
    // ------------------------------------------------------------------

    /**
     * Returns every currently connected drive annotated with its recognition
     * status.
     *
     * <p>For each mounted volume the backend runs a shallow signature scan
     * (see {@link SignatureScanner}) and reports one of:
     * <ul>
     *   <li>{@code RECOGNIZED} — a valid {@code .filesync/sync-meta.json}
     *       was found (optionally matching {@code groupId}); the response
     *       includes the stored syncRootPath, deviceId, sourceOnly, etc.</li>
     *   <li>{@code NEW} — no matching signature found.</li>
     * </ul>
     *
     * @param groupId optional — when supplied, only signatures that belong to
     *                this group cause a drive to be marked RECOGNIZED
     */
    @GetMapping("/api/devices")
    public ResponseEntity<List<DriveStatusResponse>> listDevicesWithStatus(
            @RequestParam(required = false) String groupId) {

        List<DriveInfo> drives = driveDetector.listDrives();
        List<DriveStatusResponse> responses = new ArrayList<>();

        long currentPcStateVersion = 0L;
        List<Device> dbDevices = java.util.Collections.emptyList();
        if (groupId != null) {
            dbDevices = deviceRepository.findBySyncGroupId(groupId);
            currentPcStateVersion = pcStateRepository.findById(groupId)
                    .map(PcState::getCurrentStateVersion)
                    .orElse(0L);
        }

        java.util.Set<String> matchedDeviceIds = new java.util.HashSet<>();

        for (DriveInfo drive : drives) {
            List<SyncMeta> signatures = signatureScanner
                    .scanForSignatures(Path.of(drive.path()));

            // Pick the relevant signature
            SyncMeta match = null;
            if (groupId != null) {
                match = signatures.stream()
                        .filter(m -> groupId.equals(m.getSyncGroupId()))
                        .findFirst()
                        .orElse(null);
            } else if (!signatures.isEmpty()) {
                // No group filter — take the first found signature
                match = signatures.get(0);
            }

            DriveStatusResponse dto = new DriveStatusResponse();
            dto.setDrivePath(drive.path());
            dto.setDriveLabel(drive.label());
            dto.setTotalBytes(drive.totalBytes());
            dto.setFreeBytes(drive.freeBytes());
            dto.setCurrentPcStateVersion(currentPcStateVersion);

            if (match != null) {
                dto.setStatus("RECOGNIZED");
                dto.setSyncRootPath(match.getSyncRootPath());
                dto.setSyncGroupId(match.getSyncGroupId());
                dto.setSyncGroupLabel(match.getSyncGroupLabel());
                dto.setDeviceId(match.getDeviceId());
                dto.setDeviceLabel(match.getDeviceLabel());
                dto.setSourceOnly(match.isSourceOnly());

                long deviceVersion = match.getLastSyncedPcStateVersion();
                if (match.getDeviceId() != null) {
                    matchedDeviceIds.add(match.getDeviceId());
                    Device dbDev = deviceRepository.findById(match.getDeviceId()).orElse(null);
                    if (dbDev != null) {
                        deviceVersion = dbDev.getLastSyncedPcStateVersion();
                        dto.setSourceOnly(dbDev.isSourceOnly());
                    }
                }
                dto.setLastSyncedPcStateVersion(deviceVersion);
            } else {
                dto.setStatus("NEW");
            }

            responses.add(dto);
        }

        // Include registered devices for the group that are currently unmounted/disconnected
        if (groupId != null) {
            for (Device dbDev : dbDevices) {
                if (!matchedDeviceIds.contains(dbDev.getId())) {
                    DriveStatusResponse dto = new DriveStatusResponse();
                    dto.setDrivePath(dbDev.getMountPath() != null && !dbDev.getMountPath().isBlank() ? dbDev.getMountPath() : "-");
                    dto.setDriveLabel(dbDev.getLabel());
                    dto.setStatus("RECOGNIZED");
                    dto.setConnected(false);   // drive is not currently mounted
                    dto.setSyncRootPath(dbDev.getSyncRootPath());
                    dto.setSyncGroupId(dbDev.getSyncGroupId());
                    dto.setDeviceId(dbDev.getId());
                    dto.setDeviceLabel(dbDev.getLabel());
                    dto.setSourceOnly(dbDev.isSourceOnly());
                    dto.setLastSyncedPcStateVersion(dbDev.getLastSyncedPcStateVersion());
                    dto.setCurrentPcStateVersion(currentPcStateVersion);

                    responses.add(dto);
                }
            }
        }

        return ResponseEntity.ok(responses);
    }

    // ------------------------------------------------------------------
    // GET /api/drives
    // ------------------------------------------------------------------

    /**
     * Raw enumeration of all drives / volumes currently mounted on this PC.
     * Unchanged from Phase 4 — kept as a lightweight alternative to the
     * signature-scanning {@code /api/devices} endpoint.
     */
    @GetMapping("/api/drives")
    public ResponseEntity<List<DriveInfo>> listDrives() {
        return ResponseEntity.ok(driveDetector.listDrives());
    }

    // ------------------------------------------------------------------
    // GET /api/browse?path=...
    // ------------------------------------------------------------------

    /**
     * Folder-picker endpoint: returns the immediate sub-directories of the
     * given path.  The UI calls this iteratively to let the user drill down
     * into a drive and choose a sync root folder.
     *
     * @param path absolute path to list, e.g. {@code "D:\\"}
     */
    @GetMapping("/api/browse")
    public ResponseEntity<List<FolderBrowser.FolderEntry>> browseFolders(
            @RequestParam String path) {
        return ResponseEntity.ok(folderBrowser.listSubfolders(path));
    }

    // ------------------------------------------------------------------
    // GET /api/browse-dialog
    // ------------------------------------------------------------------

    /**
     * Opens a native OS folder-picker dialog (Java Swing {@link JFileChooser}) and
     * returns the absolute path chosen by the user, or {@code 204 No Content} if
     * the dialog is cancelled.
     *
     * <p>The dialog is shown on the Swing EDT via {@code SwingUtilities.invokeAndWait}
     * so that the MVC thread blocks safely until the user makes a selection.
     *
     * @return 200 with the chosen path as a plain JSON string, or 204 if cancelled
     */
    @GetMapping("/api/browse-dialog")
    public ResponseEntity<String> openBrowseDialog() {
        AtomicReference<String> chosenPath = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setDialogTitle("Select Sync Folder");
                chooser.setAcceptAllFileFilterUsed(false);
                int result = chooser.showOpenDialog(null);
                if (result == JFileChooser.APPROVE_OPTION) {
                    chosenPath.set(chooser.getSelectedFile().getAbsolutePath());
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(500).body("Interrupted while waiting for dialog");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            cause.printStackTrace();
            return ResponseEntity.status(500).body("Error showing folder dialog: " + cause.toString());
        }
        if (chosenPath.get() == null) {
            return ResponseEntity.noContent().build(); // user cancelled
        }
        return ResponseEntity.ok(chosenPath.get());
    }

    // ------------------------------------------------------------------
    // POST /api/devices/{deviceId}/folder
    // ------------------------------------------------------------------

    /**
     * Assigns a sync folder on a connected drive to a sync group.
     *
     * <p>This creates (or updates) a {@link Device} record in the database
     * and writes a {@code .filesync/sync-meta.json} signature file into the
     * chosen folder so that the drive can be auto-recognized on future
     * connections.
     *
     * @param deviceId UUID for this device (the frontend generates one for
     *                 new drives; an existing ID can be re-used)
     * @param request  folder path, group, label, and sourceOnly flag
     */
    @PostMapping("/api/devices/{deviceId}/folder")
    @Transactional
    public ResponseEntity<?> assignFolder(
            @PathVariable String deviceId,
            @RequestBody AssignFolderRequest request) {

        // Validate the target group exists
        SyncGroup group = syncGroupRepository.findById(request.getSyncGroupId())
                .orElse(null);
        if (group == null) {
            return ResponseEntity.badRequest()
                    .body("Sync group not found: " + request.getSyncGroupId());
        }

        Path folderPath = Paths.get(request.getFolderPath());

        // Create or update the Device record
        Device device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null) {
            device = new Device(
                    deviceId,
                    request.getDeviceLabel(),
                    folderPath.getRoot() != null
                            ? folderPath.getRoot().toString() : "",
                    folderPath.toAbsolutePath().toString(),
                    request.getSyncGroupId(),
                    0L,
                    Instant.now(),
                    request.isSourceOnly()
            );
        } else {
            device.setSyncRootPath(folderPath.toAbsolutePath().toString());
            device.setMountPath(folderPath.getRoot() != null
                    ? folderPath.getRoot().toString() : "");
            device.setSyncGroupId(request.getSyncGroupId());
            device.setLabel(request.getDeviceLabel());
            device.setSourceOnly(request.isSourceOnly());
            device.setLastSeen(Instant.now());
        }
        deviceRepository.save(device);

        // Write signature file onto the drive
        try {
            signatureFileManager.writeSignature(
                    folderPath,
                    group.getId(),
                    group.getLabel(),
                    deviceId,
                    request.getDeviceLabel(),
                    0L,
                    request.isSourceOnly()
            );
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body("Failed to write signature file: " + e.getMessage());
        }

        return ResponseEntity.ok(device);
    }
}
