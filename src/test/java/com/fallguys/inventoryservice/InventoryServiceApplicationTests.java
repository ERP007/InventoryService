package com.fallguys.inventoryservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.fallguys.inventoryservice.shared.security.TestJwtDecoderConfig;

@SpringBootTest
@Import(TestJwtDecoderConfig.class)
class InventoryServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
