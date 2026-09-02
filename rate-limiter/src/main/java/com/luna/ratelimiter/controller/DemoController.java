package com.luna.ratelimiter.controller;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DemoController {
    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
            "message", "pong",
            "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/echo")
    public Map<String, Object> echo() {
        return Map.of("echoed", true, "at", Instant.now().toString());
    }
}
