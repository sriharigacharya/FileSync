package com.filesync.indexer;

import com.filesync.controller.dto.SyncPlan;
import com.filesync.model.Device;
import com.filesync.model.FileEntry;
import com.filesync.repository.DeviceRepository;
import com.filesync.repository.FileEntryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DiffEngine {

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private FileEntryRepository fileEntryRepository;

    @Autowired
    private FileIndexer fileIndexer;

    /**
     * Computes the diff for a sync group across specified connected devices (and DB records).
     *
     * @param groupId   Target sync group ID
     * @param deviceIds List of participating connected device IDs (if null/empty, uses all devices registered to the group)
     * @return SyncPlan with files and conflicts arrays
     */
    public SyncPlan computeDiff(String groupId, List<String> deviceIds) {
        List<Device> devices;
        if (deviceIds != null && !deviceIds.isEmpty()) {
            devices = deviceRepository.findAllById(deviceIds);
        } else {
            devices = deviceRepository.findBySyncGroupId(groupId);
        }

        // Map deviceId -> Scan results (relativePath -> ScannedFile)
        Map<Device, Map<String, ScannedFile>> deviceScans = new LinkedHashMap<>();
        for (Device device : devices) {
            Map<String, ScannedFile> scanMap = new HashMap<>();
            if (device.getSyncRootPath() != null && !device.getSyncRootPath().isBlank()) {
                Path root = Paths.get(device.getSyncRootPath());
                if (Files.exists(root) && Files.isDirectory(root)) {
                    try {
                        List<ScannedFile> scannedList = fileIndexer.scanFolder(root);
                        for (ScannedFile sf : scannedList) {
                            scanMap.put(sf.getRelativePath(), sf);
                        }
                    } catch (IOException e) {
                        // Inaccessible drive/folder, leave scan map empty
                    }
                }
            }
            deviceScans.put(device, scanMap);
        }

        // Fetch stored DB FileEntry rows for this group
        List<FileEntry> storedEntries = fileEntryRepository.findBySyncGroupId(groupId);

        // Collect all distinct relative paths across live scans and stored entries
        Set<String> allRelativePaths = new TreeSet<>();
        for (Map<String, ScannedFile> scanMap : deviceScans.values()) {
            allRelativePaths.addAll(scanMap.keySet());
        }
        for (FileEntry fe : storedEntries) {
            if (fe.getRelativePath() != null) {
                allRelativePaths.add(fe.getRelativePath().replace("\\", "/"));
            }
        }

        List<SyncPlan.FileDiff> filesList = new ArrayList<>();
        List<SyncPlan.ConflictDiff> conflictsList = new ArrayList<>();

        for (String relPath : allRelativePaths) {
            // Check hash of this relativePath across devices that contain it
            Map<String, String> deviceHashMap = new LinkedHashMap<>(); // deviceLabel -> hash
            for (Map.Entry<Device, Map<String, ScannedFile>> entry : deviceScans.entrySet()) {
                Device dev = entry.getKey();
                ScannedFile sf = entry.getValue().get(relPath);
                if (sf != null) {
                    deviceHashMap.put(dev.getLabel(), sf.getHash());
                }
            }

            Set<String> distinctHashesOnDevices = new HashSet<>(deviceHashMap.values());

            if (distinctHashesOnDevices.size() > 1) {
                // Conflict detected: same relativePath has different hashes on different devices
                conflictsList.add(new SyncPlan.ConflictDiff(relPath, deviceHashMap));
            } else {
                // Single hash (or 0 hashes if only in DB)
                String commonHash = null;
                if (!distinctHashesOnDevices.isEmpty()) {
                    commonHash = distinctHashesOnDevices.iterator().next();
                } else {
                    // Try to get hash from DB file entries
                    commonHash = storedEntries.stream()
                            .filter(fe -> relPath.equals(fe.getRelativePath().replace("\\", "/")))
                            .map(FileEntry::getHash)
                            .findFirst()
                            .orElse(null);
                }

                if (commonHash == null) {
                    continue;
                }

                List<String> presentOn = new ArrayList<>();
                List<String> missingOn = new ArrayList<>();

                for (Device dev : devices) {
                    Map<String, ScannedFile> scan = deviceScans.get(dev);
                    ScannedFile sf = (scan != null) ? scan.get(relPath) : null;

                    if (sf != null && commonHash.equals(sf.getHash())) {
                        presentOn.add(dev.getLabel());
                    } else {
                        // CRITICAL RULE: if device has sourceOnly = true, it must NEVER appear in missingOn
                        if (!dev.isSourceOnly()) {
                            missingOn.add(dev.getLabel());
                        }
                    }
                }

                filesList.add(new SyncPlan.FileDiff(commonHash, relPath, presentOn, missingOn));
            }
        }

        return new SyncPlan(filesList, conflictsList);
    }
}
