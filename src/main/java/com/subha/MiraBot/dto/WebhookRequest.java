package com.subha.MiraBot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookRequest {
    private QueryResult queryResult;
}