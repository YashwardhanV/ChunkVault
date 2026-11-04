package com.chunkvault;

import com.chunkvault.config.ChunkVaultProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ChunkVaultProperties.class)
public class ChunkVaultApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChunkVaultApplication.class, args);
    }
}
