package com.secureai.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ops_agent_steps", indexes = {
        @Index(name = "idx_ops_agent_steps_run_id", columnList = "run_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpsAgentStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private OpsAgentRun run;

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
