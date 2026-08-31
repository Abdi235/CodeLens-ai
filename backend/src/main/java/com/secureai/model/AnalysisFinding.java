package com.secureai.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "analysis_findings", indexes = {
        @Index(name = "idx_analysis_findings_job_id", columnList = "job_id"),
        @Index(name = "idx_analysis_findings_severity", columnList = "severity")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, length = 36)
    private String jobId;

    @Column(name = "vulnerability_type", nullable = false)
    private String vulnerabilityType;

    @Column(name = "normalized_type")
    private String normalizedType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String remediation;

    @Column(name = "retrieved_context", columnDefinition = "TEXT")
    private String retrievedContext;

    @Column(name = "ai_explanation", columnDefinition = "TEXT")
    private String aiExplanation;

    @Column(name = "classification_confidence")
    private Double classificationConfidence;

    @Column(name = "rule_id")
    private String ruleId;
}
