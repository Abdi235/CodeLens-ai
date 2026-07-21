package com.secureai.service;

import com.secureai.dto.FixGenerateRequest;
import com.secureai.dto.FixGenerateResponse;
import com.secureai.dto.ScanResponse;
import com.secureai.dto.VulnerabilityResponse;
import com.secureai.model.*;
import com.secureai.repository.ScanRepository;
import com.secureai.repository.VulnerabilityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScanService {

    private final ScanRepository scanRepository;
    private final VulnerabilityRepository vulnerabilityRepository;
    private final ProjectService projectService;
    private final CurrentUserService currentUserService;
    private final AiServiceClient aiServiceClient;
    private final ScanJobRunner scanJobRunner;

    @Transactional
    public ScanResponse startScan(Long projectId) {
        Project project = projectService.requireOwnedProject(projectId);
        Scan scan = scanRepository.save(Scan.builder()
                .project(project)
                .status(ScanStatus.PENDING)
                .startedAt(Instant.now())
                .vulnerabilityCount(0)
                .build());
        scanJobRunner.runRepositoryScan(scan.getId());
        return toScanResponse(scan);
    }

    @Transactional
    public ScanResponse startScanFromUpload(Long projectId, MultipartFile file) {
        Project project = projectService.requireOwnedProject(projectId);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Upload file is required");
        }
        Scan scan = scanRepository.save(Scan.builder()
                .project(project)
                .status(ScanStatus.PENDING)
                .startedAt(Instant.now())
                .vulnerabilityCount(0)
                .build());
        try {
            scanJobRunner.runUploadScan(scan.getId(), file.getBytes(), file.getOriginalFilename());
        } catch (Exception e) {
            scan.setStatus(ScanStatus.FAILED);
            scan.setErrorMessage(e.getMessage());
            scan.setCompletedAt(Instant.now());
            scanRepository.save(scan);
            throw new IllegalArgumentException("Failed to read upload: " + e.getMessage());
        }
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

    @Transactional(readOnly = true)
    public VulnerabilityResponse getVulnerability(Long id) {
        User user = currentUserService.requireCurrentUser();
        Vulnerability vuln = vulnerabilityRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Vulnerability not found"));
        if (!vuln.getScan().getProject().getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Vulnerability not found");
        }
        return toVulnerabilityResponse(vuln);
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
                : (vuln.getDescription() != null ? vuln.getDescription() : vuln.getType());

        String language = inferLanguage(vuln.getFileLocation());
        String after;
        String explanation;

        try {
            Map<String, Object> fix = aiServiceClient.generateFix(vuln.getType(), before, language);
            after = String.valueOf(fix.getOrDefault("after", ""));
            explanation = String.valueOf(fix.getOrDefault("explanation", vuln.getRecommendation()));
        } catch (Exception e) {
            log.warn("AI fix generation unavailable, using local template: {}", e.getMessage());
            after = localFixTemplate(vuln.getType());
            explanation = "AI service unavailable. Local template fix applied. Start ai-service on :8000 for LLM-backed fixes.";
        }

        vuln.setSuggestedFix(after);
        vuln.setAiExplanation(explanation);
        vulnerabilityRepository.save(vuln);

        return new FixGenerateResponse(vuln.getId(), before, after, explanation);
    }

    public void acceptFix(Long vulnerabilityId, boolean accepted) {
        User user = currentUserService.requireCurrentUser();
        Vulnerability vuln = vulnerabilityRepository.findById(vulnerabilityId)
                .orElseThrow(() -> new IllegalArgumentException("Vulnerability not found"));
        if (!vuln.getScan().getProject().getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Vulnerability not found");
        }
        try {
            aiServiceClient.reportFixFeedback(accepted);
        } catch (Exception e) {
            log.warn("Unable to report fix feedback: {}", e.getMessage());
        }
    }

    private String inferLanguage(String fileLocation) {
        if (fileLocation == null) {
            return "java";
        }
        String lower = fileLocation.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".py")) return "python";
        if (lower.endsWith(".js") || lower.endsWith(".jsx")) return "javascript";
        if (lower.endsWith(".ts") || lower.endsWith(".tsx")) return "typescript";
        if (lower.endsWith(".cs")) return "csharp";
        if (lower.endsWith(".go")) return "go";
        return "java";
    }

    private String localFixTemplate(String type) {
        String upper = type == null ? "" : type.toUpperCase(Locale.ROOT);
        if (upper.contains("SQL")) {
            return """
                    PreparedStatement stmt = connection.prepareStatement(
                        "SELECT * FROM users WHERE id = ?"
                    );
                    stmt.setLong(1, userId);
                    ResultSet rs = stmt.executeQuery();
                    """;
        }
        if (upper.contains("XSS")) {
            return "element.textContent = userInput;";
        }
        if (upper.contains("HARDCODED") || upper.contains("CREDENTIAL")) {
            return "String password = System.getenv(\"APP_DB_PASSWORD\");";
        }
        return "// Apply secure coding remediation for: " + type;
    }

    private ScanResponse toScanResponse(Scan scan) {
        return new ScanResponse(
                scan.getId(),
                scan.getProject().getId(),
                scan.getStatus(),
                scan.getStartedAt(),
                scan.getCompletedAt(),
                scan.getVulnerabilityCount(),
                scan.getErrorMessage()
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
