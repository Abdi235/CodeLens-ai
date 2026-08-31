package com.secureai.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analysis_jobs", indexes = {
        @Index(name = "idx_analysis_jobs_job_id", columnList = "job_id", unique = true),
        @Index(name = "idx_analysis_jobs_user_id", columnList = "user_id"),
        @Index(name = "idx_analysis_jobs_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, unique = true, length = 36)
    private String jobId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String repository;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisJobStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "finding_count")
    private Integer findingCount;

    @Column(name = "processing_attempts")
    private Integer processingAttempts;

    @Column(name = "worker_id")
    private String workerId;

    @Column(name = "processing_duration_ms")
    private Long processingDurationMs;

    @PrePersist
    void onCreate() {
        if (jobId == null) {
            jobId = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = AnalysisJobStatus.QUEUED;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (findingCount == null) {
            findingCount = 0;
        }
        if (processingAttempts == null) {
            processingAttempts = 0;
        }
    }

    public boolean canTransitionTo(AnalysisJobStatus next) {
        return switch (status) {
            case QUEUED -> next == AnalysisJobStatus.PROCESSING || next == AnalysisJobStatus.FAILED;
            case PROCESSING -> next == AnalysisJobStatus.COMPLETED || next == AnalysisJobStatus.FAILED;
            case COMPLETED, FAILED -> false;
        };
    }
}
