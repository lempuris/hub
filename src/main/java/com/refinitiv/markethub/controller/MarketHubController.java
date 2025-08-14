package com.refinitiv.markethub.controller;

import com.refinitiv.markethub.model.MarketData;
import com.refinitiv.markethub.model.MarketMakerConfig;
import com.refinitiv.markethub.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for Market Data Hub monitoring and administration.
 * 
 * Provides endpoints for:
 * - Health checks and status monitoring
 * - Market maker management
 * - Market data testing
 * - Configuration management
 * - Statistics and metrics
 * 
 * All endpoints are under /api due to server.servlet.context-path configuration.
 * 
 * @author Market Data Hub Team
 * @version 1.0
 */
@RestController
@RequestMapping("/hub")
@Slf4j
public class MarketHubController {

    @Autowired
    private FIXService fixService;

    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private MarketMakerService marketMakerService;

    @Autowired
    private HazelcastConfigService hazelcastService;

    /**
     * Gets the overall health and status of the Market Data Hub.
     * 
     * @return Health status information
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> health = new HashMap<>();
        
        // Overall status
        boolean isHealthy = fixService.isRunning() && hazelcastService.isHealthy();
        health.put("status", isHealthy ? "UP" : "DOWN");
        
        // Component status
        Map<String, String> components = new HashMap<>();
        components.put("fix", fixService.isRunning() ? "UP" : "DOWN");
        components.put("hazelcast", hazelcastService.isHealthy() ? "UP" : "DOWN");
        components.put("hazelcast_cluster_size", String.valueOf(hazelcastService.getClusterSize()));
        health.put("components", components);
        
        // Statistics
        Map<String, Object> stats = new HashMap<>();
        stats.put("active_market_makers", marketMakerService.getActiveMarketMakers().size());
        stats.put("connected_market_makers", marketMakerService.getConnectedMarketMakers().size());
        stats.put("cached_symbols", marketDataService.getAllCachedMarketData().size());
        health.put("statistics", stats);
        
        // Timestamp
        health.put("timestamp", System.currentTimeMillis());
        
        log.debug("Health check performed: {}", health);
        
        return ResponseEntity.ok(health);
    }

    /**
     * Gets detailed status information about the hub.
     * 
     * @return Detailed status information
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, String>> getStatus() {
        Map<String, String> status = new HashMap<>();
        
        // FIX status
        status.put("fix_status", fixService.getSessionStatus());
        
        // Market maker statistics
        status.put("market_maker_stats", marketMakerService.getStatistics());
        
        // Market data statistics
        status.put("market_data_stats", marketDataService.getStatistics());
        
        // Hazelcast status
        status.put("hazelcast_info", hazelcastService.getClusterInfo());
        
        return ResponseEntity.ok(status);
    }

    // ==================== Market Maker Management ====================

    /**
     * Gets all configured market makers.
     * 
     * @return List of market maker configurations
     */
    @GetMapping("/marketmakers")
    public ResponseEntity<List<MarketMakerConfig>> getMarketMakers() {
        List<MarketMakerConfig> marketMakers = marketMakerService.getAllMarketMakers();
        log.debug("Retrieved {} market makers", marketMakers.size());
        return ResponseEntity.ok(marketMakers);
    }

    /**
     * Gets a specific market maker by ID.
     * 
     * @param id The market maker ID
     * @return The market maker configuration
     */
    @GetMapping("/marketmakers/{id}")
    public ResponseEntity<MarketMakerConfig> getMarketMaker(@PathVariable Long id) {
        MarketMakerConfig marketMaker = marketMakerService.getMarketMaker(id);
        if (marketMaker == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(marketMaker);
    }

    /**
     * Creates a new market maker configuration.
     * 
     * @param config The market maker configuration to create
     * @return The created configuration
     */
    @PostMapping("/marketmakers")
    public ResponseEntity<MarketMakerConfig> createMarketMaker(@RequestBody MarketMakerConfig config) {
        log.info("Creating new market maker: {}", config.getName());
        MarketMakerConfig created = marketMakerService.createMarketMaker(config);
        return ResponseEntity.ok(created);
    }

    /**
     * Updates an existing market maker configuration.
     * 
     * @param id The market maker ID
     * @param config The updated configuration
     * @return The updated configuration
     */
    @PutMapping("/marketmakers/{id}")
    public ResponseEntity<MarketMakerConfig> updateMarketMaker(@PathVariable Long id, 
                                                               @RequestBody MarketMakerConfig config) {
        log.info("Updating market maker ID: {}", id);
        MarketMakerConfig updated = marketMakerService.updateMarketMaker(id, config);
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(updated);
    }

    /**
     * Deletes a market maker configuration.
     * 
     * @param id The market maker ID
     * @return Success response or not found
     */
    @DeleteMapping("/marketmakers/{id}")
    public ResponseEntity<Void> deleteMarketMaker(@PathVariable Long id) {
        log.info("Deleting market maker ID: {}", id);
        boolean deleted = marketMakerService.deleteMarketMaker(id);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Activates a market maker.
     * 
     * @param id The market maker ID
     * @return Success response
     */
    @PostMapping("/marketmakers/{id}/activate")
    public ResponseEntity<Void> activateMarketMaker(@PathVariable Long id) {
        log.info("Activating market maker ID: {}", id);
        marketMakerService.activateMarketMaker(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Deactivates a market maker.
     * 
     * @param id The market maker ID
     * @return Success response
     */
    @PostMapping("/marketmakers/{id}/deactivate")
    public ResponseEntity<Void> deactivateMarketMaker(@PathVariable Long id) {
        log.info("Deactivating market maker ID: {}", id);
        marketMakerService.deactivateMarketMaker(id);
        return ResponseEntity.ok().build();
    }

    // ==================== Market Data Testing ====================

    /**
     * Gets the latest market data for a symbol.
     * 
     * @param symbol The symbol to get data for
     * @return The latest market data or not found
     */
    @GetMapping("/marketdata/{symbol}")
    public ResponseEntity<MarketData> getMarketData(@PathVariable String symbol) {
        MarketData data = marketDataService.getLatestMarketData(symbol);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(data);
    }

    /**
     * Gets all cached market data.
     * 
     * @return Map of symbol to market data
     */
    @GetMapping("/marketdata")
    public ResponseEntity<Map<String, MarketData>> getAllMarketData() {
        Map<String, MarketData> allData = marketDataService.getAllCachedMarketData();
        log.debug("Retrieved {} cached market data entries", allData.size());
        return ResponseEntity.ok(allData);
    }

    /**
     * Generates and distributes test market data for a symbol.
     * Useful for testing without a real Refinitiv connection.
     * 
     * @param symbol The symbol to generate data for
     * @return The generated market data
     */
    @PostMapping("/marketdata/test/{symbol}")
    public ResponseEntity<MarketData> generateTestData(@PathVariable String symbol) {
        log.info("Generating test market data for {}", symbol);
        
        // Generate test data
        MarketData testData = marketDataService.generateTestMarketData(symbol);
        
        // Process it through the normal flow
        marketDataService.processIncomingMarketData(testData);
        
        return ResponseEntity.ok(testData);
    }

    // ==================== FIX Management ====================

    /**
     * Restarts the FIX acceptor.
     * 
     * @return Success message
     */
    @PostMapping("/fix/restart")
    public ResponseEntity<String> restartFIX() {
        log.info("Restarting FIX acceptor");
        fixService.restart();
        return ResponseEntity.ok("FIX acceptor restarted");
    }

    /**
     * Sends a test message to a specific FIX session.
     * 
     * @param targetCompId The target CompID
     * @return Success or failure message
     */
    @PostMapping("/fix/test/{targetCompId}")
    public ResponseEntity<String> sendTestMessage(@PathVariable String targetCompId) {
        log.info("Sending test message to {}", targetCompId);
        boolean sent = fixService.sendTestMessage(targetCompId);
        if (sent) {
            return ResponseEntity.ok("Test message sent to " + targetCompId);
        } else {
            return ResponseEntity.badRequest().body("Failed to send test message to " + targetCompId);
        }
    }

    /**
     * Disconnects a specific FIX session.
     * 
     * @param targetCompId The target CompID to disconnect
     * @return Success or failure message
     */
    @PostMapping("/fix/disconnect/{targetCompId}")
    public ResponseEntity<String> disconnectSession(@PathVariable String targetCompId) {
        log.info("Disconnecting FIX session for {}", targetCompId);
        boolean disconnected = fixService.disconnectSession(targetCompId);
        if (disconnected) {
            return ResponseEntity.ok("Disconnected " + targetCompId);
        } else {
            return ResponseEntity.badRequest().body("Failed to disconnect " + targetCompId);
        }
    }

    // ==================== Cache Management ====================

    /**
     * Clears the local market data cache.
     * 
     * @return Success message
     */
    @PostMapping("/cache/clear/local")
    public ResponseEntity<String> clearLocalCache() {
        log.info("Clearing local market data cache");
        marketDataService.clearLocalCache();
        return ResponseEntity.ok("Local market data cache cleared");
    }

    /**
     * Clears the Hazelcast market data cache.
     * 
     * @return Success message
     */
    @PostMapping("/cache/clear/hazelcast")
    public ResponseEntity<String> clearHazelcastCache() {
        log.info("Clearing Hazelcast market data cache");
        hazelcastService.clearMarketDataCache();
        return ResponseEntity.ok("Hazelcast market data cache cleared");
    }

    /**
     * Gets Hazelcast map statistics.
     * 
     * @return Map statistics
     */
    @GetMapping("/cache/stats")
    public ResponseEntity<String> getCacheStatistics() {
        hazelcastService.logMapStatistics();
        return ResponseEntity.ok("Cache statistics logged - check application logs");
    }

    /**
     * Reloads configurations from database to Hazelcast.
     * 
     * @return Success message
     */
    @PostMapping("/cache/reload")
    public ResponseEntity<String> reloadConfigurations() {
        log.info("Reloading configurations to Hazelcast");
        marketMakerService.reloadConfigurations();
        return ResponseEntity.ok("Configurations reloaded to Hazelcast");
    }

    // ==================== Statistics ====================

    /**
     * Resets all service statistics.
     * 
     * @return Success message
     */
    @PostMapping("/stats/reset")
    public ResponseEntity<String> resetStatistics() {
        log.info("Resetting all statistics");
        marketDataService.resetStatistics();
        return ResponseEntity.ok("Statistics reset");
    }
}