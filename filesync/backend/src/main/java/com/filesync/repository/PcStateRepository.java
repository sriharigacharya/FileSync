package com.filesync.repository;

import com.filesync.model.PcState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PcStateRepository extends JpaRepository<PcState, String> {
}
