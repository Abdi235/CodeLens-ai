package com.secureai.repository;

import com.secureai.model.OpsAgentRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OpsAgentRunRepository extends JpaRepository<OpsAgentRun, Long> {
    Optional<OpsAgentRun> findByRunId(String runId);

    List<OpsAgentRun> findTop20ByOrderByCreatedAtDesc();

    long countBySuccessTrue();

    long countBySuccessIsNotNull();
}
