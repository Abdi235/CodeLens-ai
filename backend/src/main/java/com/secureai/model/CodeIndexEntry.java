package com.secureai.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "code_index_entries", indexes = {
        @Index(name = "idx_code_index_job_id", columnList = "job_id"),
        @Index(name = "idx_code_index_file_path", columnList = "file_path")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeIndexEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, length = 36)
    private String jobId;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "start_line")
    private Integer startLine;

    @Column(name = "end_line")
    private Integer endLine;

    @Column(name = "language")
    private String language;

    @Column(name = "chunk_text", columnDefinition = "TEXT")
    private String chunkText;

    @Column(name = "token_count")
    private Integer tokenCount;
}
