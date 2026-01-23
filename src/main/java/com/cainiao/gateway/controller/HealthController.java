package com.cainiao.gateway.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("actuator/health")
public class HealthController {

    @GetMapping(value = "liveness", produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<String> liveness() {
        return Mono.just("LIVE");
    }

    @GetMapping(value = "readiness", produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<String> readiness() {
        return Mono.just("READY");
    }
}
