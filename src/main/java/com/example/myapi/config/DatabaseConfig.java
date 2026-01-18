package com.example.myapi.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * Database Configuration
 * - Ensures data directory exists before database connection
 * - Enables WAL mode for better concurrent access
 */
@Configuration
@EnableTransactionManagement
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
        
        // SQLite configuration for better concurrency
        // Note: Connection initialization SQL is set in application.yml
        // This ensures PRAGMA settings are applied to every new connection
        
        // Initialize WAL mode on first connection (redundant but ensures it's set)
        enableWalMode(dataSource);
        
        return dataSource;
    }

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("dataSource") DataSource dataSource) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "update");
        properties.put("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
        properties.put("hibernate.format_sql", "true");
        
        return builder
                .dataSource(dataSource)
                .packages("com.example.myapi.entity")
                .properties(properties)
                .build();
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(
            @Qualifier("entityManagerFactory") LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory.getObject());
    }
    
    /**
     * Enable WAL mode on database connections for better concurrency
     */
    private void enableWalMode(HikariDataSource dataSource) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            // Enable WAL mode (allows multiple readers and one writer concurrently)
            stmt.execute("PRAGMA journal_mode=WAL");
            // Set busy timeout
            stmt.execute("PRAGMA busy_timeout=30000");
            // Optimize for concurrent access
            stmt.execute("PRAGMA synchronous=NORMAL");
            stmt.execute("PRAGMA cache_size=10000");
        } catch (SQLException e) {
            System.err.println("Warning: Failed to configure SQLite WAL mode: " + e.getMessage());
        }
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
