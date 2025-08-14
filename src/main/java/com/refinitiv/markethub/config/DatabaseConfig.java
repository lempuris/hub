package com.refinitiv.markethub.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Database configuration for Market Data Hub.
 * 
 * Configures connections to:
 * 1. TimescaleDB - For optional market data storage (1 day retention for debugging)
 * 2. Operational DB - For holiday calendars and other operational data
 * 
 * Uses HikariCP for efficient connection pooling.
 * 
 * @author Market Data Hub Team
 * @version 1.0
 */
@Configuration
@Slf4j
public class DatabaseConfig {

    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/markethub}")
    private String dbUrl;

    @Value("${spring.datasource.username:markethub}")
    private String dbUsername;

    @Value("${spring.datasource.password:markethub}")
    private String dbPassword;

    @Value("${spring.datasource.driver-class-name:org.postgresql.Driver}")
    private String driverClassName;

    @Value("${timescale.enabled:false}")
    private boolean timescaleEnabled;

    /**
     * Creates the primary DataSource for operational data.
     * This includes holiday calendars, audit logs, and configuration data.
     * 
     * @return DataSource configured with HikariCP
     */
    @Bean
    @Primary
    public DataSource dataSource() {
        log.info("Configuring primary database connection to: {}", dbUrl);
        
        HikariConfig config = new HikariConfig();
        
        // Basic connection settings
        config.setJdbcUrl(dbUrl);
        config.setUsername(dbUsername);
        config.setPassword(dbPassword);
        config.setDriverClassName(driverClassName);
        
        // Connection pool settings
        // Minimum number of idle connections
        config.setMinimumIdle(5);
        // Maximum pool size
        config.setMaximumPoolSize(20);
        // Connection timeout in milliseconds
        config.setConnectionTimeout(30000);
        // Maximum lifetime of a connection in the pool (30 minutes)
        config.setMaxLifetime(1800000);
        // How long a connection can be idle before being removed
        config.setIdleTimeout(600000);
        
        // Pool name for monitoring
        config.setPoolName("MarketHub-OperationalDB");
        
        // Enable connection test on checkout for reliability
        config.setConnectionTestQuery("SELECT 1");
        
        // Performance optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        
        // PostgreSQL specific optimizations
        config.addDataSourceProperty("reWriteBatchedInserts", "true");
        
        HikariDataSource dataSource = new HikariDataSource(config);
        
        log.info("Primary database connection pool configured with max size: {}", 
                config.getMaximumPoolSize());
        
        return dataSource;
    }

    /**
     * Creates a DataSource specifically for TimescaleDB if enabled.
     * TimescaleDB is used for time-series market data storage.
     * Data is retained for 1 day for debugging purposes only.
     * 
     * @return DataSource for TimescaleDB or null if disabled
     */
    @Bean(name = "timescaleDataSource")
    public DataSource timescaleDataSource() {
        if (!timescaleEnabled) {
            log.info("TimescaleDB is disabled. Market data will not be persisted.");
            return null;
        }
        
        log.info("Configuring TimescaleDB connection for market data storage");
        
        HikariConfig config = new HikariConfig();
        
        // TimescaleDB is PostgreSQL with time-series extensions
        // Can use the same connection URL with different schema
        config.setJdbcUrl(dbUrl.replace("/markethub", "/markethub_timescale"));
        config.setUsername(dbUsername);
        config.setPassword(dbPassword);
        config.setDriverClassName(driverClassName);
        
        // Smaller pool for TimescaleDB as it's only for debugging
        config.setMinimumIdle(2);
        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(30000);
        config.setMaxLifetime(1800000);
        config.setIdleTimeout(600000);
        
        config.setPoolName("MarketHub-TimescaleDB");
        
        // TimescaleDB specific optimizations
        config.addDataSourceProperty("reWriteBatchedInserts", "true");
        // Enable batch operations for better performance
        config.addDataSourceProperty("batchMode", "true");
        // Optimize for time-series data
        config.addDataSourceProperty("assumeMinServerVersion", "12");
        
        HikariDataSource dataSource = new HikariDataSource(config);
        
        log.info("TimescaleDB connection pool configured for market data storage");
        
        return dataSource;
    }
}