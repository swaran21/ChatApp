package com.chat.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/health") // Or just /health
    public ResponseEntity<String> healthCheck() {
        // Simply return 200 OK. Avoid heavy logic or DB calls here.
        return ResponseEntity.ok("OK");
    }
}