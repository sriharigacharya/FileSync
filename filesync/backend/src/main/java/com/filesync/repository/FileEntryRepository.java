package com.filesync.repository;

import com.filesync.model.FileEntry;
import com.filesync.model.FileEntryId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FileEntryRepository extends JpaRepository<FileEntry, FileEntryId> {
    List<FileEntry> findBySyncGroupId(String syncGroupId);
    void deleteAllBySyncGroupId(String syncGroupId);
}
