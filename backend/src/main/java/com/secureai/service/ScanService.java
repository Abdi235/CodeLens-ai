package com.secureai.service;

import com.secureai.dto.FixGenerateRequest;
import com.secureai.dto.FixGenerateResponse;
import com.secureai.dto.ScanResponse;
import com.secureai.dto.VulnerabilityResponse;
import com.secureai.model.*;
import com.secureai.repository.ScanRepository;
import com.secureai.repository.VulnerabilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Week 1 stub: creates a completed scan with a sample vulnerability.
 * Week 2 will replace this with Semgrep integration.
 */
@Service
@RequiredArgsConstructor
public class ScanService {

    private final ScanRepository scanRepository;
    private final VulnerabilityRepository vulnerabilityRepository;
    private final ProjectService projectService;
    private final CurrentUserService currentUserService;

    @Transactional
    public ScanResponse startScan(Long projectId) {
        Project project = projectService.requireOwnedProject(projectId);

        Scan scan = Scan.builder()
                .project(project)
                .status(ScanStatus.RUNNING)
                .startedAt(Instant.now())
                .build();
        scan = scanRepository.save(scan);

        // Placeholder finding until Semgrep is wired in Week 2
        Vulnerability sample = Vulnerability.builder()
                .scan(scan)
                .severity(Severity.HIGH)
                .type("SQL Injection")
                .fileLocation("src/main/java/example/UserController.java")
                .lineNumber(42)
                .description("User input concatenated into SQL query without parameterization.")
                .recommendation("Use PreparedStatement or a parameterized ORM query.")
                .build();
        vulnerabilityRepository.save(sample);

        scan.setStatus(ScanStatus.COMPLETED);
        scan.setCompletedAt(Instant.now());
        scan.setVulnerabilityCount(1);
        scan = scanRepository.save(scan);

        return toScanResponse(scan);
    }

    @Transactional(readOnly = true)
    public List<ScanResponse> listScans(Long projectId) {
        projectService.requireOwnedProject(projectId);
        return scanRepository.findByProjectIdOrderByStartedAtDesc(projectId).stream()
                .map(this::toScanResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ScanResponse getScan(Long scanId) {
        User user = currentUserService.requireCurrentUser();
        Scan scan = scanRepository.findByIdAndProjectUserId(scanId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Scan not found"));
        return toScanResponse(scan);
    }

    @Transactional(readOnly = true)
    public List<VulnerabilityResponse> listProjectVulnerabilities(Long projectId) {
        projectService.requireOwnedProject(projectId);
        return scanRepository.findByProjectIdOrderByStartedAtDesc(projectId).stream()
                .flatMap(scan -> vulnerabilityRepository.findByScanIdOrderBySeverityAsc(scan.getId()).stream())
                .map(this::toVulnerabilityResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VulnerabilityResponse> listAllMine() {
        User user = currentUserService.requireCurrentUser();
        return vulnerabilityRepository.findByScanProjectUserIdOrderByIdDesc(user.getId()).stream()
                .map(this::toVulnerabilityResponse)
                .toList();
    }

    @Transactional
    public FixGenerateResponse generateFix(FixGenerateRequest request) {
        User user = currentUserService.requireCurrentUser();
        Vulnerability vuln = vulnerabilityRepository.findById(request.vulnerabilityId())
                .orElseThrow(() -> new IllegalArgumentException("Vulnerability not found"));

        if (!vuln.getScan().getProject().getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Vulnerability not found");
        }

        String before = request.codeSnippet() != null && !request.codeSnippet().isBlank()
                ? request.codeSnippet()
                : "statement.execute(query);";

        String after = """
                PreparedStatement stmt = connection.prepareStatement(
                    "SELECT * FROM users WHERE id = ?"
                );
                stmt.setLong(1, userId);
                ResultSet rs = stmt.executeQuery();
                """;

        String explanation = "Replace string-concatenated SQL with a parameterized PreparedStatement "
                + "so user input cannot alter query structure.";

        vuln.setSuggestedFix(after);
        vuln.setAiExplanation(explanation);
        vulnerabilityRepository.save(vuln);

        return new FixGenerateResponse(vuln.getId(), before, after.trim(), explanation);
    }

    private ScanResponse toScanResponse(Scan scan) {
        return new ScanResponse(
                scan.getId(),
                scan.getProject().getId(),
                scan.getStatus(),
                scan.getStartedAt(),
                scan.getCompletedAt(),
                scan.getVulnerabilityCount()
        );
    }

    private VulnerabilityResponse toVulnerabilityResponse(Vulnerability vuln) {
        return new VulnerabilityResponse(
                vuln.getId(),
                vuln.getScan().getId(),
                vuln.getSeverity(),
                vuln.getType(),
                vuln.getFileLocation(),
                vuln.getLineNumber(),
                vuln.getDescription(),
                vuln.getRecommendation(),
                vuln.getAiExplanation(),
                vuln.getSuggestedFix()
        );
    }
}
