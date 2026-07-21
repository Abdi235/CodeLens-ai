package com.secureai.controller;

import com.secureai.dto.FixGenerateRequest;
import com.secureai.dto.FixGenerateResponse;
import com.secureai.service.ScanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fix")
@RequiredArgsConstructor
public class FixController {

    private final ScanService scanService;

    @PostMapping("/generate")
    public FixGenerateResponse generate(@Valid @RequestBody FixGenerateRequest request) {
        return scanService.generateFix(request);
    }

    public record FixFeedbackRequest(@NotNull Boolean accepted) {}

    @PostMapping("/{vulnerabilityId}/feedback")
    public void feedback(@PathVariable Long vulnerabilityId, @Valid @RequestBody FixFeedbackRequest request) {
        scanService.acceptFix(vulnerabilityId, Boolean.TRUE.equals(request.accepted()));
    }
}
