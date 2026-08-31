package com.secureai.repository;

import com.secureai.model.RepositoryRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RepositoryRecordRepository extends JpaRepository<RepositoryRecord, Long> {
    Optional<RepositoryRecord> findByJobId(String jobId);
    Optional<RepositoryRecord> findByIdAndUserId(Long id, Long userId);
}
