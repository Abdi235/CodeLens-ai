package com.secureai.repository;

import com.secureai.model.Scan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScanRepository extends JpaRepository<Scan, Long> {
    List<Scan> findByProjectIdOrderByStartedAtDesc(Long projectId);
    Optional<Scan> findByIdAndProjectUserId(Long id, Long userId);
}
