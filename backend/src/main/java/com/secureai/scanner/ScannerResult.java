package com.secureai.scanner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScannerResult {
    private String path;
    private List<String> engines = new ArrayList<>();
    @JsonProperty("vulnerability_count")
    private int vulnerabilityCount;
    private List<ScannerFinding> findings = new ArrayList<>();
}
