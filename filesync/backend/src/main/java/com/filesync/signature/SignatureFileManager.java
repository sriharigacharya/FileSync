package com.filesync.signature;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

@Service
public class SignatureFileManager {
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Convenience overload — sourceOnly defaults to false for normal devices. */
    public void writeSignature(Path folder, String syncGroupId, String syncGroupLabel,
                               String deviceId, String deviceLabel,
                               long lastSyncedPcStateVersion) throws IOException {
        writeSignature(folder, syncGroupId, syncGroupLabel, deviceId, deviceLabel,
                lastSyncedPcStateVersion, false);
    }

    public void writeSignature(Path folder, String syncGroupId, String syncGroupLabel,
                               String deviceId, String deviceLabel,
                               long lastSyncedPcStateVersion, boolean sourceOnly) throws IOException {
        Path filesyncDir = folder.resolve(".filesync");
        if (!Files.exists(filesyncDir)) {
            Files.createDirectories(filesyncDir);
        }

        Path metaFile = filesyncDir.resolve("sync-meta.json");
        SyncMeta meta = new SyncMeta();
        meta.setSyncGroupId(syncGroupId);
        meta.setSyncGroupLabel(syncGroupLabel);
        meta.setDeviceId(deviceId);
        meta.setDeviceLabel(deviceLabel);
        meta.setSyncRootPath(folder.toAbsolutePath().toString());
        meta.setCreatedAt(Instant.now().toString());
        meta.setLastSyncedPcStateVersion(lastSyncedPcStateVersion);
        meta.setSourceOnly(sourceOnly);

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(metaFile.toFile(), meta);
    }

    public Optional<SyncMeta> readSignature(Path folder) {
        Path metaFile = folder.resolve(".filesync").resolve("sync-meta.json");
        if (Files.exists(metaFile)) {
            try {
                return Optional.ofNullable(objectMapper.readValue(metaFile.toFile(), SyncMeta.class));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return Optional.empty();
    }

    public void updateLastSyncedVersion(Path folder, long newVersion) throws IOException {
        Optional<SyncMeta> metaOpt = readSignature(folder);
        if (metaOpt.isPresent()) {
            SyncMeta meta = metaOpt.get();
            meta.setLastSyncedPcStateVersion(newVersion);
            Path metaFile = folder.resolve(".filesync").resolve("sync-meta.json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(metaFile.toFile(), meta);
        }
    }
}
