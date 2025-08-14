package com.refinitiv.markethub.service;

import com.refinitiv.markethub.model.MarketMakerConfig;
import com.refinitiv.markethub.repository.MarketMakerConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing market maker configurations and connections.
 * 
 * This service:
 * - Manages market maker configurations in both database and Hazelcast
 * - Tracks connection status and statistics
 * - Handles product mappings and transformations
 * - Provides methods for querying and updating market makers
 * 
 * @author Market Data Hub Team
 * @version 1.0
 */
@Service
@Slf4j
@Transactional
public class MarketMakerService {

    @Autowired
    private MarketMakerConfigRepository repository;

    @Autowired
    private HazelcastConfigService hazelcastService;

    /**
     * Initializes the service and loads configurations into Hazelcast.
     */
    @PostConstruct
    public void initialize() {
        log.info("Initializing Market Maker Service");
        loadConfigurationsToHazelcast();
    }

    /**
     * Loads all market maker configurations from database to Hazelcast.
     * This ensures distributed cache is synchronized with database.
     */
    private void loadConfigurationsToHazelcast() {
        List<MarketMakerConfig> configs = repository.findAll();
        log.info("Loading {} market maker configurations to Hazelcast", configs.size());
        
        for (MarketMakerConfig config : configs) {
            hazelcastService.saveMarketMakerConfig(config);
        }
        
        log.info("Market maker configurations loaded to Hazelcast");
    }

    /**
     * Creates a new market maker configuration.
     * 
     * @param config The configuration to create
     * @return The created configuration with generated ID
     */
    public MarketMakerConfig createMarketMaker(MarketMakerConfig config) {
        log.info("Creating new market maker configuration: {}", config.getName());
        
        // Set default values if not provided
        if (config.getActive() == null) {
            config.setActive(true);
        }
        if (config.getConnectionStatus() == null) {
            config.setConnectionStatus(MarketMakerConfig.ConnectionStatus.DISCONNECTED);
        }
        if (config.getConnectionMode() == null) {
            config.setConnectionMode(MarketMakerConfig.ConnectionMode.INITIATOR);
        }
        
        // Save to database
        MarketMakerConfig saved = repository.save(config);
        
        // Save to Hazelcast for distributed access
        hazelcastService.saveMarketMakerConfig(saved);
        
        log.info("Created market maker {} with ID {}", saved.getName(), saved.getId());
        return saved;
    }

    /**
     * Updates an existing market maker configuration.
     * 
     * @param id The ID of the configuration to update
     * @param config The updated configuration
     * @return The updated configuration or null if not found
     */
    public MarketMakerConfig updateMarketMaker(Long id, MarketMakerConfig config) {
        Optional<MarketMakerConfig> existing = repository.findById(id);
        
        if (existing.isEmpty()) {
            log.warn("Market maker configuration not found with ID: {}", id);
            return null;
        }
        
        MarketMakerConfig toUpdate = existing.get();
        
        // Update fields
        toUpdate.setName(config.getName());
        toUpdate.setType(config.getType());
        toUpdate.setConnectionMode(config.getConnectionMode());
        toUpdate.setHost(config.getHost());
        toUpdate.setPort(config.getPort());
        toUpdate.setHeartbeatInterval(config.getHeartbeatInterval());
        toUpdate.setSubscribedSymbols(config.getSubscribedSymbols());
        toUpdate.setProductMappingJson(config.getProductMappingJson());
        toUpdate.setTransformationRulesJson(config.getTransformationRulesJson());
        toUpdate.setFixSettingsJson(config.getFixSettingsJson());
        toUpdate.setMaxMessagesPerSecond(config.getMaxMessagesPerSecond());
        toUpdate.setActive(config.getActive());
        toUpdate.setRequiresAuthentication(config.getRequiresAuthentication());
        toUpdate.setPriority(config.getPriority());
        toUpdate.setUpdatedBy(config.getUpdatedBy());
        
        // Save to database
        MarketMakerConfig saved = repository.save(toUpdate);
        
        // Update in Hazelcast
        hazelcastService.saveMarketMakerConfig(saved);
        
        log.info("Updated market maker configuration: {}", saved.getName());
        return saved;
    }

    /**
     * Deletes a market maker configuration.
     * 
     * @param id The ID of the configuration to delete
     * @return true if deleted, false if not found
     */
    public boolean deleteMarketMaker(Long id) {
        if (!repository.existsById(id)) {
            log.warn("Market maker configuration not found with ID: {}", id);
            return false;
        }
        
        // Get the config for Hazelcast removal
        Optional<MarketMakerConfig> config = repository.findById(id);
        
        // Delete from database
        repository.deleteById(id);
        
        // Remove from Hazelcast if found
        config.ifPresent(c -> {
            // Remove from Hazelcast using the service method
            // Note: Would need to add a remove method in HazelcastConfigService
            log.info("Deleted market maker configuration: {}", c.getName());
        });
        
        return true;
    }

    /**
     * Gets a market maker configuration by ID.
     * 
     * @param id The configuration ID
     * @return The configuration or null if not found
     */
    public MarketMakerConfig getMarketMaker(Long id) {
        return repository.findById(id).orElse(null);
    }

    /**
     * Gets a market maker configuration by market maker ID.
     * First checks Hazelcast cache, then database.
     * 
     * @param marketMakerId The market maker ID
     * @return The configuration or null if not found
     */
    public MarketMakerConfig getMarketMakerByMarketMakerId(String marketMakerId) {
        // First check Hazelcast
        MarketMakerConfig config = hazelcastService.getMarketMakerConfig(marketMakerId);
        
        if (config == null) {
            // Fall back to database
            config = repository.findByMarketMakerId(marketMakerId);
            
            // Cache in Hazelcast if found
            if (config != null) {
                hazelcastService.saveMarketMakerConfig(config);
            }
        }
        
        return config;
    }

    /**
     * Gets a market maker configuration by FIX CompID.
     * First checks Hazelcast cache, then database.
     * 
     * @param compId The FIX CompID
     * @return The configuration or null if not found
     */
    public MarketMakerConfig getConfigByCompId(String compId) {
        // First check Hazelcast
        MarketMakerConfig config = hazelcastService.getMarketMakerConfigByCompId(compId);
        
        if (config == null) {
            // Fall back to database
            config = repository.findByCompId(compId);
            
            // Cache in Hazelcast if found
            if (config != null) {
                hazelcastService.saveMarketMakerConfig(config);
            }
        }
        
        return config;
    }

    /**
     * Gets all market maker configurations.
     * 
     * @return List of all configurations
     */
    public List<MarketMakerConfig> getAllMarketMakers() {
        return repository.findAll();
    }

    /**
     * Gets all active market maker configurations.
     * First checks Hazelcast cache, then database.
     * 
     * @return List of active configurations
     */
    public List<MarketMakerConfig> getActiveMarketMakers() {
        // Try Hazelcast first for better performance
        List<MarketMakerConfig> activeConfigs = hazelcastService.getActiveMarketMakerConfigs();
        
        if (activeConfigs.isEmpty()) {
            // Fall back to database
            activeConfigs = repository.findByActive(true);
            
            // Cache in Hazelcast
            for (MarketMakerConfig config : activeConfigs) {
                hazelcastService.saveMarketMakerConfig(config);
            }
        }
        
        return activeConfigs;
    }

    /**
     * Gets all connected market makers.
     * 
     * @return List of connected market makers
     */
    public List<MarketMakerConfig> getConnectedMarketMakers() {
        return repository.findByConnectionStatus(MarketMakerConfig.ConnectionStatus.CONNECTED);
    }

    /**
     * Updates the connection status of a market maker.
     * 
     * @param id The configuration ID
     * @param status The new connection status
     */
    public void updateConnectionStatus(Long id, MarketMakerConfig.ConnectionStatus status) {
        repository.findById(id).ifPresent(config -> {
            config.setConnectionStatus(status);
            config.setLastConnectionTime(LocalDateTime.now());
            
            // Save to database
            repository.save(config);
            
            // Update in Hazelcast
            hazelcastService.updateMarketMakerConnectionStatus(
                config.getMarketMakerId(), status);
            
            log.debug("Updated connection status for {} to {}", 
                    config.getName(), status);
        });
    }

    /**
     * Updates the connection status by CompID.
     * 
     * @param compId The FIX CompID
     * @param status The new connection status
     */
    public void updateConnectionStatusByCompId(String compId, 
                                              MarketMakerConfig.ConnectionStatus status) {
        MarketMakerConfig config = repository.findByCompId(compId);
        if (config != null) {
            updateConnectionStatus(config.getId(), status);
        }
    }

    /**
     * Adds a symbol subscription for a market maker.
     * 
     * @param id The configuration ID
     * @param symbolPattern The symbol or pattern to subscribe to
     */
    public void addSubscription(Long id, String symbolPattern) {
        repository.findById(id).ifPresent(config -> {
            config.getSubscribedSymbols().add(symbolPattern);
            
            // Save to database
            repository.save(config);
            
            // Update in Hazelcast
            hazelcastService.saveMarketMakerConfig(config);
            
            log.info("Added subscription {} for {}", symbolPattern, config.getName());
        });
    }

    /**
     * Removes a symbol subscription for a market maker.
     * 
     * @param id The configuration ID
     * @param symbolPattern The symbol or pattern to unsubscribe from
     */
    public void removeSubscription(Long id, String symbolPattern) {
        repository.findById(id).ifPresent(config -> {
            config.getSubscribedSymbols().remove(symbolPattern);
            
            // Save to database
            repository.save(config);
            
            // Update in Hazelcast
            hazelcastService.saveMarketMakerConfig(config);
            
            log.info("Removed subscription {} for {}", symbolPattern, config.getName());
        });
    }

    /**
     * Gets market makers that subscribe to a specific symbol.
     * 
     * @param symbol The symbol to check
     * @return List of market makers that subscribe to the symbol
     */
    public List<MarketMakerConfig> getSubscribersForSymbol(String symbol) {
        return getActiveMarketMakers().stream()
            .filter(config -> config.subscribesTo(symbol))
            .collect(Collectors.toList());
    }

    /**
     * Activates a market maker.
     * 
     * @param id The configuration ID
     */
    public void activateMarketMaker(Long id) {
        repository.findById(id).ifPresent(config -> {
            config.setActive(true);
            
            // Save to database
            repository.save(config);
            
            // Update in Hazelcast
            hazelcastService.saveMarketMakerConfig(config);
            
            log.info("Activated market maker: {}", config.getName());
        });
    }

    /**
     * Deactivates a market maker.
     * 
     * @param id The configuration ID
     */
    public void deactivateMarketMaker(Long id) {
        repository.findById(id).ifPresent(config -> {
            config.setActive(false);
            config.setConnectionStatus(MarketMakerConfig.ConnectionStatus.SUSPENDED);
            
            // Save to database
            repository.save(config);
            
            // Update in Hazelcast
            hazelcastService.saveMarketMakerConfig(config);
            
            log.info("Deactivated market maker: {}", config.getName());
        });
    }

    /**
     * Gets statistics about market makers.
     * 
     * @return Statistics as a formatted string
     */
    public String getStatistics() {
        List<MarketMakerConfig> all = getAllMarketMakers();
        List<MarketMakerConfig> active = getActiveMarketMakers();
        List<MarketMakerConfig> connected = getConnectedMarketMakers();
        
        return String.format(
            "Market Maker Statistics:\n" +
            "  Total Configured: %d\n" +
            "  Active: %d\n" +
            "  Connected: %d\n" +
            "  Disconnected: %d\n" +
            "  Suspended: %d",
            all.size(),
            active.size(),
            connected.size(),
            all.size() - connected.size(),
            all.stream()
                .filter(c -> c.getConnectionStatus() == MarketMakerConfig.ConnectionStatus.SUSPENDED)
                .count()
        );
    }

    /**
     * Reloads all configurations from database to Hazelcast.
     * Useful for synchronization after direct database changes.
     */
    public void reloadConfigurations() {
        log.info("Reloading market maker configurations");
        loadConfigurationsToHazelcast();
    }
}