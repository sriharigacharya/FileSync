package com.filesync.repository;

import com.filesync.model.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DeviceRepository extends JpaRepository<Device, String> {
    List<Device> findBySyncGroupId(String syncGroupId);
    void deleteAllBySyncGroupId(String syncGroupId);
}
