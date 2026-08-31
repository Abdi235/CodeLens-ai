package com.secureai.controller;

import com.secureai.dto.CodeSearchResponse;
import com.secureai.dto.RepositoryRecordResponse;
import com.secureai.service.CodeSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class CodeSearchController {

    private final CodeSearchService codeSearchService;

    @GetMapping("/api/search")
    public CodeSearchResponse search(
            @RequestParam String jobId,
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int limit
    ) {
        if (q.isBlank()) {
            throw new IllegalArgumentException("Search query is required");
        }
        return codeSearchService.search(jobId, q.trim(), limit);
    }

    @GetMapping("/api/repositories/{id}")
    public RepositoryRecordResponse getRepository(@PathVariable Long id) {
        return codeSearchService.getRepository(id);
    }

    @GetMapping("/api/repositories/by-job/{jobId}")
    public RepositoryRecordResponse getRepositoryByJob(@PathVariable String jobId) {
        return codeSearchService.getRepositoryByJobId(jobId);
    }
}
