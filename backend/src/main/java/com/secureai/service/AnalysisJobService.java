package com.secureai.service;

import com.secureai.dto.*;
import com.secureai.messaging.AnalysisJobMessage;
import com.secureai.messaging.AnalysisJobPublisher;
import com.secureai.messaging.JobStatusMessage;
import com.secureai.model.*;
import com.secureai.repository.AnalysisFindingRepository;
import com.secureai.repository.AnalysisJobRepository;
import com.secureai.validation.RepositoryUrlValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisJobService {

    private final AnalysisJobRepository jobRepository;
    private final AnalysisFindingRepository findingRepository;
    private final AnalysisJobPublisher jobPublisher;
    private final CurrentUserService currentUserService;
    private final RepositoryUrlValidator repositoryUrlValidator;

    @Transactional
    public AnalysisJobResponse createJob(AnalysisCreateRequest request) {
        repositoryUrlValidator.validate(request.repository());
        User user = currentUserService.requireCurrentUser();

        AnalysisJob job = AnalysisJob.builder()
                .jobId(UUID.randomUUID().toString())
                .user(user)
                .repository(request.repository().trim())
                .status(AnalysisJobStatus.QUEUED)
                .build();
        job = jobRepository.save(job);

        log.info("Created analysis job jobId={} userId={}", job.getJobId(), user.getId());
        jobPublisher.publish(new AnalysisJobMessage(job.getJobId(), job.getRepository(), user.getId(), 1));

        return toResponse(job);
    }

    @Transactional
    public void applyStatusUpdate(JobStatusMessage message) {
        AnalysisJob job = jobRepository.findByJobId(message.jobId()).orElse(null);
        if (job == null) {
            log.warn("Status update for unknown jobId={}", message.jobId());
            return;
        }

        AnalysisJobStatus next;
        try {
            next = AnalysisJobStatus.valueOf(message.status());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid status {} for jobId={}", message.status(), message.jobId());
            return;
        }

        if (!job.canTransitionTo(next) && job.getStatus() != next) {
            log.warn("Ignored invalid transition jobId={} {} -> {}", job.getJobId(), job.getStatus(), next);
            return;
        }

        job.setStatus(next);
        if (next == AnalysisJobStatus.PROCESSING && job.getStartedAt() == null) {
            job.setStartedAt(Instant.now());
            job.setProcessingAttempts(job.getProcessingAttempts() + 1);
        }
        if (next == AnalysisJobStatus.COMPLETED || next == AnalysisJobStatus.FAILED) {
            job.setCompletedAt(Instant.now());
        }
        if (message.errorMessage() != null) {
            job.setErrorMessage(message.errorMessage());
        }
        if (message.findingCount() != null) {
            job.setFindingCount(message.findingCount());
        }
        jobRepository.save(job);
        log.info("Job status updated jobId={} status={}", job.getJobId(), next);
    }

    @Transactional(readOnly = true)
    public AnalysisJobResponse getJob(String jobId) {
        User user = currentUserService.requireCurrentUser();
        AnalysisJob job = jobRepository.findByJobIdAndUserId(jobId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Analysis job not found"));
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public AnalysisResultsResponse getResults(String jobId) {
        User user = currentUserService.requireCurrentUser();
        AnalysisJob job = jobRepository.findByJobIdAndUserId(jobId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Analysis job not found"));

        List<AnalysisFindingResponse> findings = findingRepository.findByJobIdOrderBySeverityAsc(jobId).stream()
                .map(this::toFindingResponse)
                .toList();

        return new AnalysisResultsResponse(
                job.getJobId(),
                job.getStatus().name(),
                job.getFindingCount(),
                findings
        );
    }

    @Transactional(readOnly = true)
    public List<AnalysisJobResponse> listJobs() {
        User user = currentUserService.requireCurrentUser();
        return jobRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    private AnalysisJobResponse toResponse(AnalysisJob job) {
        return new AnalysisJobResponse(
                job.getJobId(),
                job.getRepository(),
                job.getStatus(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getErrorMessage(),
                job.getFindingCount()
        );
    }

    private AnalysisFindingResponse toFindingResponse(AnalysisFinding f) {
        return new AnalysisFindingResponse(
                f.getId(),
                f.getVulnerabilityType(),
                f.getNormalizedType(),
                f.getSeverity(),
                f.getFilePath(),
                f.getLineNumber(),
                f.getDescription(),
                f.getRemediation(),
                f.getRetrievedContext(),
                f.getAiExplanation(),
                f.getClassificationConfidence(),
                f.getRuleId()
        );
    }
}
