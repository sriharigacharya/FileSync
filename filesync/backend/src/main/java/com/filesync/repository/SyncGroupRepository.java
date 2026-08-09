package com.filesync.repository;

import com.filesync.model.SyncGroup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncGroupRepository extends JpaRepository<SyncGroup, String> {
}
