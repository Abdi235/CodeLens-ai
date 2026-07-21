package com.secureai.service;

import com.secureai.model.Scan;
import com.secureai.repository.ScanRepository;
import com.secureai.scanner.ScannerResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScanJobRunner {

    private final ScanRepository scanRepository;
    private final WorkspaceService workspaceService;
    private final AiServiceClient aiServiceClient;
    private final ScanPersistenceService scanPersistenceService;

    @Async("scanExecutor")
    public void runRepositoryScan(Long scanId) {
        try {
            Scan scan = scanRepository.findById(scanId).orElse(null);
            if (scan == null) {
                return;
            }
            scanPersistenceService.markRunning(scanId);
            Path workDir = workspaceService.prepareProjectWorkspace(scan.getProject().getId(), scanId);
            Path sourceDir = resolveSource(scan.getProject().getRepositoryUrl(), workDir);
            ScannerResult result = aiServiceClient.scanPath(sourceDir.toString());
            scanPersistenceService.persistFindings(scanId, result);
        } catch (Exception e) {
            log.error("Scan {} failed", scanId, e);
            scanPersistenceService.failScan(scanId, e.getMessage());
        }
    }

    @Async("scanExecutor")
    public void runUploadScan(Long scanId, byte[] zipBytes, String filename) {
        try {
            scanPersistenceService.markRunning(scanId);
            ScannerResult result = aiServiceClient.scanZip(zipBytes, filename);
            scanPersistenceService.persistFindings(scanId, result);
        } catch (Exception e) {
            log.error("Upload scan {} failed", scanId, e);
            scanPersistenceService.failScan(scanId, e.getMessage());
        }
    }

    private Path resolveSource(String repositoryUrl, Path workDir) throws Exception {
        if (repositoryUrl != null && (repositoryUrl.startsWith("http://")
                || repositoryUrl.startsWith("https://")
                || repositoryUrl.startsWith("git@"))) {
            return workspaceService.cloneRepository(repositoryUrl, workDir.resolve("repo"));
        }
        return workspaceService.resolveLocalOrSample(repositoryUrl, workDir.resolve("repo"));
    }
}
