package com.secureai.controller;

import com.secureai.dto.*;
import com.secureai.service.OpsAgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ops-agent")
@RequiredArgsConstructor
public class OpsAgentController {

    private final OpsAgentService opsAgentService;

    @PostMapping("/run")
    public OpsAgentRunResponse run(@Valid @RequestBody OpsAgentRunRequest request) {
        return opsAgentService.run(request);
    }

    @PostMapping("/simulate")
    public OpsAgentRunResponse simulate(@Valid @RequestBody OpsAgentSimulateRequest request) {
        return opsAgentService.simulate(request);
    }

    @GetMapping("/runs")
    public List<OpsAgentRunResponse> listRuns() {
        return opsAgentService.listRuns();
    }

    @GetMapping("/runs/{runId}")
    public OpsAgentRunResponse getRun(@PathVariable String runId) {
        return opsAgentService.getRun(runId);
    }

    @GetMapping("/eval")
    public OpsAgentEvalResponse eval() {
        return opsAgentService.eval();
    }
}
