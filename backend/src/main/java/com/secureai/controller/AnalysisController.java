package com.secureai.controller;

import com.secureai.dto.*;
import com.secureai.service.AnalysisJobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisJobService analysisJobService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnalysisJobResponse create(@Valid @RequestBody AnalysisCreateRequest request) {
        return analysisJobService.createJob(request);
    }

    @GetMapping
    public List<AnalysisJobResponse> list() {
        return analysisJobService.listJobs();
    }

    @GetMapping("/{jobId}")
    public AnalysisJobResponse get(@PathVariable String jobId) {
        return analysisJobService.getJob(jobId);
    }

    @GetMapping("/{jobId}/results")
    public AnalysisResultsResponse results(@PathVariable String jobId) {
        return analysisJobService.getResults(jobId);
    }
}
