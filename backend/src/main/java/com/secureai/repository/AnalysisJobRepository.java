package com.secureai.repository;

import com.secureai.model.AnalysisJob;
import com.secureai.model.AnalysisJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {
    Optional<AnalysisJob> findByJobId(String jobId);

    Optional<AnalysisJob> findByJobIdAndUserId(String jobId, Long userId);

    List<AnalysisJob> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByStatus(AnalysisJobStatus status);

    @Query("select avg(j.processingDurationMs) from AnalysisJob j where j.processingDurationMs is not null")
    Double averageProcessingDurationMs();

    @Query("""
            select j from AnalysisJob j
            where j.status in :statuses
              and (
                   (j.status = com.secureai.model.AnalysisJobStatus.QUEUED and j.createdAt < :cutoff)
                or (j.status = com.secureai.model.AnalysisJobStatus.PROCESSING
                    and coalesce(j.startedAt, j.createdAt) < :cutoff)
              )
            order by j.createdAt asc
            """)
    List<AnalysisJob> findStuckJobs(
            @Param("statuses") List<AnalysisJobStatus> statuses,
            @Param("cutoff") Instant cutoff
    );
}
