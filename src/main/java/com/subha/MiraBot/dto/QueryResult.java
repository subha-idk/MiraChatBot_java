package com.subha.MiraBot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class QueryResult {
    private Map<String, Object> parameters;
    private Intent intent;
    private List<OutputContext> outputContexts;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Intent {
        private String displayName;
    }
}