package com.secureai.controller;

import com.secureai.dto.FindingsAgentEvalResponse;
import com.secureai.dto.FindingsAgentRunRequest;
import com.secureai.dto.FindingsAgentRunResponse;
import com.secureai.dto.FindingsAgentSimulateRequest;
import com.secureai.service.FindingsAgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/findings-agent")
@RequiredArgsConstructor
public class FindingsAgentController {

    private final FindingsAgentService findingsAgentService;

    @PostMapping("/run")
    public FindingsAgentRunResponse run(@Valid @RequestBody FindingsAgentRunRequest request) {
        return findingsAgentService.run(request);
    }

    @PostMapping("/simulate")
    public FindingsAgentRunResponse simulate(@Valid @RequestBody FindingsAgentSimulateRequest request) {
        return findingsAgentService.simulate(request);
    }

    @GetMapping("/runs")
    public List<FindingsAgentRunResponse> listRuns() {
        return findingsAgentService.listRuns();
    }

    @GetMapping("/runs/{runId}")
    public FindingsAgentRunResponse getRun(@PathVariable String runId) {
        return findingsAgentService.getRun(runId);
    }

    @GetMapping("/eval")
    public FindingsAgentEvalResponse eval() {
        return findingsAgentService.eval();
    }
}
