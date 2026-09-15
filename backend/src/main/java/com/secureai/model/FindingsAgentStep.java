package com.secureai.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "findings_agent_steps", indexes = {
        @Index(name = "idx_findings_agent_steps_run_id", columnList = "run_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FindingsAgentStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private FindingsAgentRun run;

    @Column(name = "step_index", nullable = false)
    private int stepIndex;

    @Column(name = "role", nullable = false, length = 32)
    private String role;

    @Column(name = "tool_name", length = 64)
    private String toolName;

    @Column(name = "tool_args", columnDefinition = "TEXT")
    private String toolArgsJson;

    @Column(name = "tool_result", columnDefinition = "TEXT")
    private String toolResultJson;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
