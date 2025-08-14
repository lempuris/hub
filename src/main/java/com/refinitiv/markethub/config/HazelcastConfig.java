package com.refinitiv.markethub.config;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.spring.context.SpringManagedContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Hazelcast configuration for distributed configuration management.
 * 
 * Hazelcast is used as the distributed cache and configuration store for:
 * - Instrument configurations that can be shared across instances
 * - Market maker connection configurations
 * - Product mapping configurations
 * - Temporary market data caching (if needed)
 * 
 * This allows for configuration changes without code deployment and
 * enables horizontal scaling of the hub.
 * 
 * @author Market Data Hub Team
 * @version 1.0
 */
@Configuration
@Slf4j
public class HazelcastConfig {

    /**
     * Creates the main Hazelcast configuration.
     * Sets up the distributed data grid for configuration management.
     * 
     * @return Hazelcast Config object
     */
    @Bean
    public Config hazelcastConfiguration() {
        log.info("Configuring Hazelcast for distributed configuration management");
        
        Config config = new Config();
        
        // Set the instance name for identification
        config.setInstanceName("markethub-hazelcast");
        
        // Configure network settings
        NetworkConfig networkConfig = config.getNetworkConfig();
        
        // Set the port for Hazelcast communication
        networkConfig.setPort(5701);
        networkConfig.setPortAutoIncrement(true);
        networkConfig.setPortCount(10);
        
        // Disable multicast and use TCP/IP for cloud deployments
        networkConfig.getJoin().getMulticastConfig().setEnabled(false);
        networkConfig.getJoin().getTcpIpConfig()
            .setEnabled(true)
            // Add member addresses for cluster formation
            // In production, these would come from configuration
            .addMember("127.0.0.1:5701")
            .addMember("127.0.0.1:5702");
        
        // Configure instrument configuration map
        MapConfig instrumentMapConfig = new MapConfig();
        instrumentMapConfig.setName("instruments");
        // Time-to-live: -1 means no expiration
        instrumentMapConfig.setTimeToLiveSeconds(-1);
        // Max idle time: -1 means no idle timeout
        instrumentMapConfig.setMaxIdleSeconds(-1);
        // Backup count for redundancy
        instrumentMapConfig.setBackupCount(1);
        // Async backup for better performance
        instrumentMapConfig.setAsyncBackupCount(1);
        // Statistics for monitoring
        instrumentMapConfig.setStatisticsEnabled(true);
        
        config.addMapConfig(instrumentMapConfig);
        
        // Configure market maker configuration map
        MapConfig marketMakerMapConfig = new MapConfig();
        marketMakerMapConfig.setName("marketmakers");
        marketMakerMapConfig.setTimeToLiveSeconds(-1);
        marketMakerMapConfig.setMaxIdleSeconds(-1);
        marketMakerMapConfig.setBackupCount(1);
        marketMakerMapConfig.setAsyncBackupCount(1);
        marketMakerMapConfig.setStatisticsEnabled(true);
        
        config.addMapConfig(marketMakerMapConfig);
        
        // Configure product mapping configuration map
        MapConfig productMappingConfig = new MapConfig();
        productMappingConfig.setName("productmappings");
        productMappingConfig.setTimeToLiveSeconds(-1);
        productMappingConfig.setMaxIdleSeconds(-1);
        productMappingConfig.setBackupCount(1);
        productMappingConfig.setAsyncBackupCount(1);
        productMappingConfig.setStatisticsEnabled(true);
        
        config.addMapConfig(productMappingConfig);
        
        // Configure temporary market data cache (1 hour TTL for debugging)
        MapConfig marketDataCacheConfig = new MapConfig();
        marketDataCacheConfig.setName("marketdatacache");
        // Keep market data for 1 hour for debugging purposes
        marketDataCacheConfig.setTimeToLiveSeconds(3600);
        marketDataCacheConfig.setMaxIdleSeconds(1800);
        // No backup needed for temporary cache
        marketDataCacheConfig.setBackupCount(0);
        marketDataCacheConfig.setStatisticsEnabled(true);
        // Eviction policy to prevent memory issues
        marketDataCacheConfig.getEvictionConfig()
            .setMaxSizePolicy(com.hazelcast.config.MaxSizePolicy.USED_HEAP_SIZE)
            .setSize(100); // Max 100MB for market data cache
        
        config.addMapConfig(marketDataCacheConfig);
        
        // Configure holiday calendar cache
        MapConfig holidayCalendarConfig = new MapConfig();
        holidayCalendarConfig.setName("holidaycalendar");
        // Holiday data rarely changes, cache for 24 hours
        holidayCalendarConfig.setTimeToLiveSeconds(86400);
        holidayCalendarConfig.setMaxIdleSeconds(43200);
        holidayCalendarConfig.setBackupCount(1);
        holidayCalendarConfig.setStatisticsEnabled(true);
        
        config.addMapConfig(holidayCalendarConfig);
        
        // Enable management center for monitoring
        config.getManagementCenterConfig()
            .setConsoleEnabled(true);
        
        // Set cluster name for identification
        config.setClusterName("markethub-cluster");
        
        log.info("Hazelcast configuration completed with cluster name: {}", 
                config.getClusterName());
        
        return config;
    }

    /**
     * Creates a SpringManagedContext for Hazelcast.
     * This allows Hazelcast to use Spring-managed beans.
     * 
     * @return SpringManagedContext for dependency injection
     */
    @Bean
    public SpringManagedContext managedContext() {
        return new SpringManagedContext();
    }
}