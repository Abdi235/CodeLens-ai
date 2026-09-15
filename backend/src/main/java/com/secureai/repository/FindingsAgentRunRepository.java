package com.secureai.repository;

import com.secureai.model.FindingsAgentRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FindingsAgentRunRepository extends JpaRepository<FindingsAgentRun, Long> {
    Optional<FindingsAgentRun> findByRunId(String runId);

    List<FindingsAgentRun> findTop20ByOrderByCreatedAtDesc();
}
