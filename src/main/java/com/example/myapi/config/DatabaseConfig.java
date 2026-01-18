package com.example.myapi.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Database Configuration
 * - Ensures data directory exists before database connection
 */
@Configuration
public class DatabaseConfig {

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties properties) {
        // Ensure data directory exists before creating DataSource
        ensureDataDirectoryExists();
        
        // Create DataSource using the configured properties
        HikariDataSource dataSource = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        
        return dataSource;
    }

    private void ensureDataDirectoryExists() {
        try {
            // Extract directory path from JDBC URL
            // Format: jdbc:sqlite:./data/dashboard.db
            String dbPath = datasourceUrl.replace("jdbc:sqlite:", "");
            Path dbFilePath = Paths.get(dbPath);
            Path dataDir = dbFilePath.getParent();
            
            if (dataDir != null && !dataDir.toFile().exists()) {
                Files.createDirectories(dataDir);
                System.out.println("Created data directory: " + dataDir.toAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("Failed to create data directory: " + e.getMessage());
            throw new RuntimeException("Cannot create data directory", e);
        }
    }
}
