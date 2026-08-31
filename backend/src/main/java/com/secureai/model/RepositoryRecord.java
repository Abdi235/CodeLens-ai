package com.secureai.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "repository_records", indexes = {
        @Index(name = "idx_repository_records_job_id", columnList = "job_id", unique = true),
        @Index(name = "idx_repository_records_user_id", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositoryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, unique = true, length = 36)
    private String jobId;

    @Column(name = "repository_url", nullable = false, columnDefinition = "TEXT")
    private String repositoryUrl;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "file_count")
    private Integer fileCount;

    @Column(name = "indexed_chunk_count")
    private Integer indexedChunkCount;

    @Column(name = "primary_language")
    private String primaryLanguage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
