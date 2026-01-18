package com.example.myapi.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Database Configuration
 * - Ensures data directory exists before database connection
 */
@Configuration
public class DatabaseConfig {

    @PostConstruct
    public void ensureDataDirectoryExists() {
        try {
            // Get the current working directory
            Path dataDir = Paths.get("./data");
            File dir = dataDir.toFile();
            
            if (!dir.exists()) {
                Files.createDirectories(dataDir);
                System.out.println("Created data directory: " + dataDir.toAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("Failed to create data directory: " + e.getMessage());
            throw new RuntimeException("Cannot create data directory", e);
        }
    }
}
