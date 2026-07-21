package com.secureai.controller;

import com.secureai.dto.ProjectRequest;
import com.secureai.dto.ProjectResponse;
import com.secureai.dto.ScanResponse;
import com.secureai.dto.VulnerabilityResponse;
import com.secureai.service.ProjectService;
import com.secureai.service.ScanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ScanService scanService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@Valid @RequestBody ProjectRequest request) {
        return projectService.create(request);
    }

    @GetMapping
    public List<ProjectResponse> list() {
        return projectService.listMine();
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable Long id) {
        return projectService.getMine(id);
    }

    @PostMapping("/{id}/scan")
    @ResponseStatus(HttpStatus.CREATED)
    public ScanResponse scan(@PathVariable Long id) {
        return scanService.startScan(id);
    }

    @GetMapping("/{id}/scans")
    public List<ScanResponse> scans(@PathVariable Long id) {
        return scanService.listScans(id);
    }

    @GetMapping("/{id}/vulnerabilities")
    public List<VulnerabilityResponse> vulnerabilities(@PathVariable Long id) {
        return scanService.listProjectVulnerabilities(id);
    }
}
