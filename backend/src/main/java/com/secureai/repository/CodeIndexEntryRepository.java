package com.secureai.repository;

import com.secureai.model.CodeIndexEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CodeIndexEntryRepository extends JpaRepository<CodeIndexEntry, Long> {
    List<CodeIndexEntry> findByJobId(String jobId);
}
