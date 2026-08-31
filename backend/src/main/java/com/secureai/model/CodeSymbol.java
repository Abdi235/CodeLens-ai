package com.secureai.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "code_symbols", indexes = {
        @Index(name = "idx_code_symbols_job_id", columnList = "job_id"),
        @Index(name = "idx_code_symbols_name", columnList = "name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeSymbol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, length = 36)
    private String jobId;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String kind;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "language")
    private String language;
}
