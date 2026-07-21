package com.secureai.scanner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.secureai.model.Severity;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScannerFinding {
    @JsonProperty("rule_id")
    private String ruleId;
    private String type;
    private Severity severity;
    @JsonProperty("file_location")
    private String fileLocation;
    @JsonProperty("line_number")
    private Integer lineNumber;
    private String description;
    private String recommendation;
    @JsonProperty("code_snippet")
    private String codeSnippet;
    private String engine;
}
