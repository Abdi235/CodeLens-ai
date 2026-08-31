package com.secureai.repository;

import com.secureai.model.AnalysisFinding;
import com.secureai.model.Severity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnalysisFindingRepository extends JpaRepository<AnalysisFinding, Long> {
    List<AnalysisFinding> findByJobIdOrderBySeverityAsc(String jobId);
    void deleteByJobId(String jobId);
    long countByJobId(String jobId);
}
