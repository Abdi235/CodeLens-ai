package com.secureai.controller;

import com.secureai.dto.ScanResponse;
import com.secureai.dto.VulnerabilityResponse;
import com.secureai.service.ScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ScanService scanService;

    @GetMapping("/api/reports/{id}")
    public ScanResponse getReport(@PathVariable Long id) {
        return scanService.getScan(id);
    }

    @GetMapping("/api/vulnerabilities")
    public List<VulnerabilityResponse> listVulnerabilities() {
        return scanService.listAllMine();
    }
}
