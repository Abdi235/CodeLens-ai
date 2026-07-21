package com.secureai.controller;

import com.secureai.dto.FixGenerateRequest;
import com.secureai.dto.FixGenerateResponse;
import com.secureai.service.ScanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fix")
@RequiredArgsConstructor
public class FixController {

    private final ScanService scanService;

    @PostMapping("/generate")
    public FixGenerateResponse generate(@Valid @RequestBody FixGenerateRequest request) {
        return scanService.generateFix(request);
    }
}
