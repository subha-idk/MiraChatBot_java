package com.subha.MiraBot.controller;


import com.subha.MiraBot.dto.WebhookRequest;
import com.subha.MiraBot.dto.WebhookResponse;
import com.subha.MiraBot.service.WebhookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebhookController {

    @Autowired
    private WebhookService webhookService;

    @PostMapping("/")
    public ResponseEntity<WebhookResponse> handleRequest(@RequestBody WebhookRequest request) {
        WebhookResponse response = webhookService.handleRequest(request);
        return ResponseEntity.ok(response);
    }
}
