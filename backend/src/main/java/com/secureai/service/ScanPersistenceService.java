package com.secureai.service;

import com.secureai.model.Scan;
import com.secureai.model.ScanStatus;
import com.secureai.model.Severity;
import com.secureai.model.Vulnerability;
import com.secureai.repository.ScanRepository;
import com.secureai.repository.VulnerabilityRepository;
import com.secureai.scanner.ScannerFinding;
import com.secureai.scanner.ScannerResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScanPersistenceService {

    private final ScanRepository scanRepository;
    private final VulnerabilityRepository vulnerabilityRepository;
    private final AiServiceClient aiServiceClient;

    @Transactional
    public void markRunning(Long scanId) {
        scanRepository.findById(scanId).ifPresent(scan -> {
            scan.setStatus(ScanStatus.RUNNING);
            scan.setStartedAt(Instant.now());
            scanRepository.save(scan);
        });
    }

    @Transactional
    public void persistFindings(Long scanId, ScannerResult result) {
        Scan managed = scanRepository.findById(scanId).orElse(null);
        if (managed == null) {
            return;
        }
        if (result == null || result.getFindings() == null) {
            managed.setStatus(ScanStatus.FAILED);
            managed.setErrorMessage("Empty scanner response. Is the AI service running on port 8000?");
            managed.setCompletedAt(Instant.now());
            scanRepository.save(managed);
            return;
        }

        for (ScannerFinding finding : result.getFindings()) {
            Vulnerability vuln = Vulnerability.builder()
                    .scan(managed)
                    .severity(finding.getSeverity() != null ? finding.getSeverity() : Severity.MEDIUM)
                    .type(finding.getType() != null ? finding.getType() : "Security Finding")
                    .fileLocation(finding.getFileLocation())
                    .lineNumber(finding.getLineNumber())
                    .description(finding.getDescription())
                    .recommendation(finding.getRecommendation())
                    .build();

            try {
                Map<String, Object> explanation = aiServiceClient.explain(
                        vuln.getType(),
                        finding.getCodeSnippet(),
                        vuln.getSeverity().name(),
                        vuln.getFileLocation()
                );
                if (explanation != null) {
                    Object summary = explanation.get("developer_summary");
                    Object why = explanation.get("why_dangerous");
                    vuln.setAiExplanation(summary != null ? summary.toString() : (why != null ? why.toString() : null));
                }
            } catch (Exception e) {
                log.warn("AI explain failed for scan {}: {}", managed.getId(), e.getMessage());
            }

            vulnerabilityRepository.save(vuln);
        }

        managed.setVulnerabilityCount(result.getFindings().size());
        managed.setStatus(ScanStatus.COMPLETED);
        managed.setCompletedAt(Instant.now());
        managed.setErrorMessage(null);
        scanRepository.save(managed);
    }

    @Transactional
    public void failScan(Long scanId, String message) {
        scanRepository.findById(scanId).ifPresent(scan -> {
            scan.setStatus(ScanStatus.FAILED);
            scan.setErrorMessage(message);
            scan.setCompletedAt(Instant.now());
            scanRepository.save(scan);
        });
    }
}
