package com.fallguys.inventoryservice;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class HealthController {
    @GetMapping("/health")
    String health() {
        return "user-service ok";
    }
}
