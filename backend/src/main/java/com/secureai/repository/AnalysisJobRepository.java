package com.secureai.repository;

import com.secureai.model.AnalysisJob;
import com.secureai.model.AnalysisJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {
    Optional<AnalysisJob> findByJobId(String jobId);
    Optional<AnalysisJob> findByJobIdAndUserId(String jobId, Long userId);
    List<AnalysisJob> findByUserIdOrderByCreatedAtDesc(Long userId);
    long countByStatus(AnalysisJobStatus status);

    @Query("select avg(j.processingDurationMs) from AnalysisJob j where j.processingDurationMs is not null")
    Double averageProcessingDurationMs();
}
