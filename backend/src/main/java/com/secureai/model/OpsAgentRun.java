package com.secureai.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ops_agent_runs", indexes = {
        @Index(name = "idx_ops_agent_runs_run_id", columnList = "run_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpsAgentRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, unique = true, length = 36)
    private String runId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 64)
    private String mode;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String goal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OpsAgentRunStatus status;

    @Column(name = "brain_type", nullable = false, length = 64)
    private String brainType;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun;

    @Column(name = "scenario", length = 64)
    private String scenario;

    @Column(name = "success")
    private Boolean success;

    @Column(name = "expected_tools", columnDefinition = "TEXT")
    private String expectedToolsJson;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("stepIndex ASC")
    @Builder.Default
    private List<OpsAgentStep> steps = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (runId == null) {
            runId = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = OpsAgentRunStatus.RUNNING;
        }
    }

    public void addStep(OpsAgentStep step) {
        step.setRun(this);
        steps.add(step);
    }
}
