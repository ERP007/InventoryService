package com.fallguys.inventoryservice;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inventory")
public class HealthController {
    @GetMapping("/health")
    ResponseEntity<String> health() {
        return ResponseEntity.ok("user-service ok");
    }
}
