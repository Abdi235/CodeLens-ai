package com.secureai.service;

import com.secureai.scanner.ScannerFinding;
import com.secureai.scanner.ScannerResult;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiServiceClient {

    private final RestClient aiRestClient;

    public ScannerResult scanPath(String path) {
        return aiRestClient.post()
                .uri("/scan/path")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("path", path))
                .retrieve()
                .body(ScannerResult.class);
    }

    public ScannerResult scanZip(byte[] zipBytes, String filename) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(zipBytes) {
            @Override
            public String getFilename() {
                return filename != null ? filename : "upload.zip";
            }
        }).contentType(MediaType.APPLICATION_OCTET_STREAM);

        MultiValueMap<String, ?> multipart = builder.build();
        return aiRestClient.post()
                .uri("/scan/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipart)
                .retrieve()
                .body(ScannerResult.class);
    }

    public Map<String, Object> explain(String issueType, String code, String severity, String fileLocation) {
        return aiRestClient.post()
                .uri("/explain")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "issue_type", issueType,
                        "code", code == null ? "" : code,
                        "severity", severity == null ? "" : severity,
                        "file_location", fileLocation == null ? "" : fileLocation
                ))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public Map<String, Object> generateFix(String issueType, String code, String language) {
        return aiRestClient.post()
                .uri("/fix")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "issue_type", issueType,
                        "code", code == null ? "" : code,
                        "language", language == null ? "java" : language
                ))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public void reportFixFeedback(boolean accepted) {
        aiRestClient.post()
                .uri("/metrics/fix-feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("accepted", accepted))
                .retrieve()
                .toBodilessEntity();
    }

    public Map<String, Object> metrics() {
        return aiRestClient.get()
                .uri("/metrics")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public boolean isHealthy() {
        try {
            Map<String, Object> body = aiRestClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (body == null) {
                return false;
            }
            Object status = body.get("status");
            return status == null || "ok".equalsIgnoreCase(String.valueOf(status))
                    || "UP".equalsIgnoreCase(String.valueOf(status));
        } catch (Exception e) {
            return false;
        }
    }
}
