package com.refinitiv.markethub.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Main application configuration class for Market Data Hub V1.
 * This class sets up core Spring Boot configurations including:
 * - Async processing for non-blocking operations
 * - Scheduling for periodic tasks
 * - Thread pool configuration for concurrent processing
 * 
 * @author Market Data Hub Team
 * @version 1.0
 */
@Configuration
@EnableAsync
@EnableScheduling
@Slf4j
public class ApplicationConfig {

    /**
     * Configures the async executor for handling concurrent market data processing.
     * This executor is used for:
     * - Processing incoming market data from Refinitiv
     * - Distributing data to multiple market makers in parallel
     * - Handling FIX message processing asynchronously
     * 
     * @return Configured ThreadPoolTaskExecutor
     */
    @Bean(name = "marketDataExecutor")
    public Executor marketDataExecutor() {
        log.info("Configuring market data executor thread pool");
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Core pool size - minimum number of threads always running
        executor.setCorePoolSize(10);
        
        // Maximum pool size - maximum threads that can be created
        executor.setMaxPoolSize(50);
        
        // Queue capacity for pending tasks
        executor.setQueueCapacity(1000);
        
        // Thread name prefix for easier debugging and monitoring
        executor.setThreadNamePrefix("MarketDataHub-");
        
        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        
        // Maximum wait time for tasks to complete during shutdown
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        
        log.info("Market data executor configured with core pool size: {}, max pool size: {}", 
                executor.getCorePoolSize(), executor.getMaxPoolSize());
        
        return executor;
    }
}