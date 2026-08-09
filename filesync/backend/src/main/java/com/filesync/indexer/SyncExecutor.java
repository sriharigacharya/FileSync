package com.filesync.indexer;

import com.filesync.controller.dto.SyncExecutionResult;
import com.filesync.controller.dto.SyncPlan;
import com.filesync.model.Device;
import com.filesync.model.FileEntry;
import com.filesync.model.PcState;
import com.filesync.repository.DeviceRepository;
import com.filesync.repository.FileEntryRepository;
import com.filesync.repository.PcStateRepository;
import com.filesync.signature.SignatureFileManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class SyncExecutor {

    @Autowired
    private DiffEngine diffEngine;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private FileEntryRepository fileEntryRepository;

    @Autowired
    private PcStateRepository pcStateRepository;

    @Autowired
    private SignatureFileManager signatureFileManager;

    @Autowired
    private SyncProgressTracker syncProgressTracker;

    /**
     * Executes copy operations for approved file hashes across participating devices.
     */
    public SyncExecutionResult executeSync(String groupId, List<String> approvedHashes, List<String> deviceIds) {
        if (approvedHashes == null || approvedHashes.isEmpty()) {
            PcState currentPcState = pcStateRepository.findById(groupId).orElse(new PcState(groupId, 0L));
            return new SyncExecutionResult(0, 0, currentPcState.getCurrentStateVersion(), "SUCCESS");
        }

        Set<String> approvedSet = new HashSet<>(approvedHashes);

        // Fetch current diff plan
        SyncPlan plan = diffEngine.computeDiff(groupId, deviceIds);

        // Fetch participating devices
        List<Device> devices;
        if (deviceIds != null && !deviceIds.isEmpty()) {
            devices = deviceRepository.findAllById(deviceIds);
        } else {
            devices = deviceRepository.findBySyncGroupId(groupId);
        }

        Map<String, Device> labelToDevice = new HashMap<>();
        Device pcDevice = null;
        for (Device dev : devices) {
            labelToDevice.put(dev.getLabel(), dev);
            if ("PC".equalsIgnoreCase(dev.getLabel())) {
                pcDevice = dev;
            }
        }

        // Calculate total files and total bytes to copy for progress tracking
        int totalFilesToCopy = 0;
        long totalBytesToCopy = 0;

        for (SyncPlan.FileDiff fileDiff : plan.getFiles()) {
            if (!approvedSet.contains(fileDiff.getHash())) continue;
            Device sourceDevice = resolveSourceDevice(fileDiff.getPresentOn(), labelToDevice);
            if (sourceDevice == null || sourceDevice.getSyncRootPath() == null) continue;
            Path sourcePath = Paths.get(sourceDevice.getSyncRootPath(), fileDiff.getRelativePath());
            if (!Files.exists(sourcePath) || !Files.isRegularFile(sourcePath)) continue;

            for (Device targetDevice : devices) {
                if (targetDevice.getId().equals(sourceDevice.getId())) continue;
                if (targetDevice.isSourceOnly()) continue;
                if (targetDevice.getSyncRootPath() == null || targetDevice.getSyncRootPath().isBlank()) continue;

                Path targetPath = Paths.get(targetDevice.getSyncRootPath(), fileDiff.getRelativePath());
                if (Files.exists(targetPath) && Files.isRegularFile(targetPath)) {
                    String existingHash = computeSha256(targetPath);
                    if (fileDiff.getHash().equals(existingHash)) continue;
                }
                totalFilesToCopy++;
                try {
                    totalBytesToCopy += Files.size(sourcePath);
                } catch (IOException e) {
                    // Ignore for sizing
                }
            }
        }

        if (!syncProgressTracker.startSync(groupId, totalFilesToCopy, totalBytesToCopy)) {
            throw new IllegalStateException("Sync already in progress for group: " + groupId);
        }

        int copiedCount = 0;
        int skippedCount = 0;
        boolean pcAcquiredNewFiles = false;

        try {
            for (SyncPlan.FileDiff fileDiff : plan.getFiles()) {
                if (!approvedSet.contains(fileDiff.getHash())) {
                    continue; // Not approved for sync by user
                }

                // Pick source device
                Device sourceDevice = resolveSourceDevice(fileDiff.getPresentOn(), labelToDevice);
                if (sourceDevice == null || sourceDevice.getSyncRootPath() == null) {
                    continue;
                }

                Path sourcePath = Paths.get(sourceDevice.getSyncRootPath(), fileDiff.getRelativePath());
                if (!Files.exists(sourcePath) || !Files.isRegularFile(sourcePath)) {
                    continue;
                }

                // Target devices: all participating devices except sourceDevice and sourceOnly devices
                for (Device targetDevice : devices) {
                    if (targetDevice.getId().equals(sourceDevice.getId())) {
                        continue; // Skip source device itself
                    }
                    if (targetDevice.isSourceOnly()) {
                        continue; // CRITICAL RULE: Never write to a sourceOnly device
                    }
                    if (targetDevice.getSyncRootPath() == null || targetDevice.getSyncRootPath().isBlank()) {
                        continue;
                    }

                    Path targetPath = Paths.get(targetDevice.getSyncRootPath(), fileDiff.getRelativePath());

                    // If file already exists on target device, check hash
                    if (Files.exists(targetPath) && Files.isRegularFile(targetPath)) {
                        String existingHash = computeSha256(targetPath);
                        if (fileDiff.getHash().equals(existingHash)) {
                            // Same hash — executor's own skip logic!
                            skippedCount++;
                            continue;
                        } else {
                            // Conflict case: different content — write as "<stem> (from <deviceLabel>)<ext>"
                            targetPath = resolveConflictPath(targetPath, sourceDevice.getLabel());
                        }
                    }

                    // Perform chunked copy with progress tracking
                    try {
                        copyChunked(sourcePath, targetPath, groupId, fileDiff.getRelativePath());
                        copiedCount++;
                        syncProgressTracker.completeFile(groupId);

                        if (pcDevice != null && pcDevice.getId().equals(targetDevice.getId())) {
                            pcAcquiredNewFiles = true;

                            // Insert FileEntry into DB for PC
                            FileEntry fe = new FileEntry(
                                    fileDiff.getHash(),
                                    groupId,
                                    fileDiff.getRelativePath(),
                                    Files.size(targetPath),
                                    sourceDevice.getId(),
                                    Instant.now()
                            );
                            fileEntryRepository.save(fe);
                        }
                    } catch (IOException e) {
                        System.err.println("Failed to copy file to " + targetPath + ": " + e.getMessage());
                    }
                }
            }

            syncProgressTracker.finishSync(groupId);
        } catch (Exception e) {
            syncProgressTracker.failSync(groupId, e.getMessage());
            throw e;
        }

        // Handle PcState versioning
        PcState pcState = pcStateRepository.findById(groupId).orElseGet(() -> {
            PcState s = new PcState(groupId, 0L);
            return pcStateRepository.save(s);
        });

        if (pcAcquiredNewFiles || copiedCount > 0) {
            pcState.setCurrentStateVersion(pcState.getCurrentStateVersion() + 1);
            pcStateRepository.save(pcState);
        }

        long finalVersion = pcState.getCurrentStateVersion();

        // Update lastSyncedPcStateVersion on DB + Signature files for all participating devices
        for (Device dev : devices) {
            dev.setLastSyncedPcStateVersion(finalVersion);
            dev.setLastSeen(Instant.now());
            deviceRepository.save(dev);

            if (dev.getSyncRootPath() != null && !dev.getSyncRootPath().isBlank()) {
                Path devRoot = Paths.get(dev.getSyncRootPath());
                if (Files.exists(devRoot) && Files.isDirectory(devRoot)) {
                    try {
                        signatureFileManager.updateLastSyncedVersion(devRoot, finalVersion);
                    } catch (IOException e) {
                        System.err.println("Failed to update signature on " + devRoot + ": " + e.getMessage());
                    }
                }
            }
        }

        return new SyncExecutionResult(copiedCount, skippedCount, finalVersion, "SUCCESS");
    }

    private Device resolveSourceDevice(List<String> presentOnLabels, Map<String, Device> labelToDevice) {
        if (presentOnLabels == null || presentOnLabels.isEmpty()) {
            return null;
        }

        // Prefer PC if available
        for (String label : presentOnLabels) {
            if ("PC".equalsIgnoreCase(label) && labelToDevice.containsKey(label)) {
                return labelToDevice.get(label);
            }
        }

        // Otherwise pick the first accessible device
        for (String label : presentOnLabels) {
            Device dev = labelToDevice.get(label);
            if (dev != null && dev.getSyncRootPath() != null) {
                Path p = Paths.get(dev.getSyncRootPath());
                if (Files.exists(p)) {
                    return dev;
                }
            }
        }

        return null;
    }

    private Path resolveConflictPath(Path originalPath, String sourceDeviceLabel) {
        Path parent = originalPath.getParent();
        String filename = originalPath.getFileName().toString();
        int dotIdx = filename.lastIndexOf('.');
        String stem;
        String ext;
        if (dotIdx > 0) {
            stem = filename.substring(0, dotIdx);
            ext = filename.substring(dotIdx);
        } else {
            stem = filename;
            ext = "";
        }

        String newFilename = stem + " (from " + sourceDeviceLabel + ")" + ext;
        return (parent != null) ? parent.resolve(newFilename) : Paths.get(newFilename);
    }

    private void copyChunked(Path src, Path dest, String groupId, String relativePath) throws IOException {
        if (dest.getParent() != null) {
            Files.createDirectories(dest.getParent());
        }
        try (InputStream in = Files.newInputStream(src);
             OutputStream out = Files.newOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                syncProgressTracker.updateChunk(groupId, relativePath, bytesRead);
            }
        }
    }

    private String computeSha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            try (InputStream is = Files.newInputStream(file)) {
                while ((bytesRead = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
