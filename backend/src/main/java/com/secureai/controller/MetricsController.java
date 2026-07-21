package com.secureai.controller;

import com.secureai.service.AiServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final AiServiceClient aiServiceClient;

    @GetMapping("/ai")
    public Map<String, Object> aiMetrics() {
        try {
            return aiServiceClient.metrics();
        } catch (Exception e) {
            return Map.of(
                    "error", "AI service unavailable",
                    "message", e.getMessage() != null ? e.getMessage() : "unreachable"
            );
        }
    }
}
