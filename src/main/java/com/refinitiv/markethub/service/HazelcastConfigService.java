package com.refinitiv.markethub.service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.refinitiv.markethub.model.Instrument;
import com.refinitiv.markethub.model.MarketData;
import com.refinitiv.markethub.model.MarketMakerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Service for managing distributed configuration using Hazelcast.
 * 
 * This service provides:
 * - Distributed storage of instrument configurations
 * - Market maker configurations
 * - Product mappings
 * - Temporary market data caching
 * - Holiday calendar caching
 * 
 * All data stored in Hazelcast is available across all hub instances,
 * enabling horizontal scaling and high availability.
 * 
 * @author Market Data Hub Team
 * @version 1.0
 */
@Service
@Slf4j
public class HazelcastConfigService {

    @Autowired
    private HazelcastInstance hazelcastInstance;

    /**
     * Distributed map for instrument configurations.
     * Key: Symbol, Value: Instrument
     */
    private IMap<String, Instrument> instrumentMap;

    /**
     * Distributed map for market maker configurations.
     * Key: Market Maker ID, Value: MarketMakerConfig
     */
    private IMap<String, MarketMakerConfig> marketMakerMap;

    /**
     * Distributed map for product mappings.
     * Key: "MM_ID:Symbol", Value: Mapped Symbol
     */
    private IMap<String, String> productMappingMap;

    /**
     * Distributed map for temporary market data cache.
     * Key: Symbol, Value: MarketData
     * TTL: 1 hour
     */
    private IMap<String, MarketData> marketDataCache;

    /**
     * Distributed map for holiday calendar cache.
     * Key: "Market:Date", Value: Holiday info
     */
    private IMap<String, String> holidayCache;

    /**
     * Initializes Hazelcast maps after Spring context is ready.
     */
    @PostConstruct
    public void initialize() {
        log.info("Initializing Hazelcast configuration service");
        
        // Get references to distributed maps
        instrumentMap = hazelcastInstance.getMap("instruments");
        marketMakerMap = hazelcastInstance.getMap("marketmakers");
        productMappingMap = hazelcastInstance.getMap("productmappings");
        marketDataCache = hazelcastInstance.getMap("marketdatacache");
        holidayCache = hazelcastInstance.getMap("holidaycalendar");
        
        log.info("Hazelcast maps initialized successfully");
        logMapStatistics();
    }

    // ==================== Instrument Configuration Methods ====================

    /**
     * Stores an instrument configuration in Hazelcast.
     * 
     * @param instrument The instrument to store
     */
    public void saveInstrument(Instrument instrument) {
        if (instrument == null || instrument.getSymbol() == null) {
            log.warn("Cannot save null instrument or instrument without symbol");
            return;
        }
        
        instrumentMap.put(instrument.getSymbol(), instrument);
        log.debug("Saved instrument {} to Hazelcast", instrument.getSymbol());
    }

    /**
     * Retrieves an instrument configuration by symbol.
     * 
     * @param symbol The instrument symbol
     * @return The instrument or null if not found
     */
    public Instrument getInstrument(String symbol) {
        return instrumentMap.get(symbol);
    }

    /**
     * Retrieves all instrument configurations.
     * 
     * @return List of all instruments
     */
    public List<Instrument> getAllInstruments() {
        return new ArrayList<>(instrumentMap.values());
    }

    /**
     * Retrieves all active instrument configurations.
     * 
     * @return List of active instruments
     */
    public List<Instrument> getActiveInstruments() {
        return instrumentMap.values().stream()
            .filter(Instrument::getActive)
            .collect(Collectors.toList());
    }

    /**
     * Removes an instrument configuration.
     * 
     * @param symbol The instrument symbol to remove
     */
    public void removeInstrument(String symbol) {
        instrumentMap.remove(symbol);
        log.debug("Removed instrument {} from Hazelcast", symbol);
    }

    /**
     * Bulk updates instrument configurations.
     * 
     * @param instruments Map of instruments to update
     */
    public void bulkUpdateInstruments(Map<String, Instrument> instruments) {
        instrumentMap.putAll(instruments);
        log.info("Bulk updated {} instruments in Hazelcast", instruments.size());
    }

    // ==================== Market Maker Configuration Methods ====================

    /**
     * Stores a market maker configuration.
     * 
     * @param config The market maker configuration
     */
    public void saveMarketMakerConfig(MarketMakerConfig config) {
        if (config == null || config.getMarketMakerId() == null) {
            log.warn("Cannot save null market maker config or config without ID");
            return;
        }
        
        marketMakerMap.put(config.getMarketMakerId(), config);
        log.debug("Saved market maker config {} to Hazelcast", config.getMarketMakerId());
    }

    /**
     * Retrieves a market maker configuration by ID.
     * 
     * @param marketMakerId The market maker ID
     * @return The configuration or null if not found
     */
    public MarketMakerConfig getMarketMakerConfig(String marketMakerId) {
        return marketMakerMap.get(marketMakerId);
    }

    /**
     * Retrieves a market maker configuration by CompID.
     * 
     * @param compId The FIX CompID
     * @return The configuration or null if not found
     */
    public MarketMakerConfig getMarketMakerConfigByCompId(String compId) {
        return marketMakerMap.values().stream()
            .filter(config -> compId.equals(config.getCompId()))
            .findFirst()
            .orElse(null);
    }

    /**
     * Retrieves all market maker configurations.
     * 
     * @return List of all market maker configurations
     */
    public List<MarketMakerConfig> getAllMarketMakerConfigs() {
        return new ArrayList<>(marketMakerMap.values());
    }

    /**
     * Retrieves all active market maker configurations.
     * 
     * @return List of active market maker configurations
     */
    public List<MarketMakerConfig> getActiveMarketMakerConfigs() {
        return marketMakerMap.values().stream()
            .filter(MarketMakerConfig::getActive)
            .collect(Collectors.toList());
    }

    /**
     * Updates the connection status of a market maker.
     * 
     * @param marketMakerId The market maker ID
     * @param status The new connection status
     */
    public void updateMarketMakerConnectionStatus(String marketMakerId, 
                                                  MarketMakerConfig.ConnectionStatus status) {
        MarketMakerConfig config = marketMakerMap.get(marketMakerId);
        if (config != null) {
            config.setConnectionStatus(status);
            config.setLastConnectionTime(LocalDateTime.now());
            marketMakerMap.put(marketMakerId, config);
            log.debug("Updated connection status for {} to {}", marketMakerId, status);
        }
    }

    // ==================== Product Mapping Methods ====================

    /**
     * Stores a product mapping for a market maker.
     * 
     * @param marketMakerId The market maker ID
     * @param sourceSymbol The source symbol
     * @param mappedSymbol The mapped symbol
     */
    public void saveProductMapping(String marketMakerId, String sourceSymbol, String mappedSymbol) {
        String key = marketMakerId + ":" + sourceSymbol;
        productMappingMap.put(key, mappedSymbol);
        log.debug("Saved product mapping {} -> {} for {}", sourceSymbol, mappedSymbol, marketMakerId);
    }

    /**
     * Retrieves a product mapping.
     * 
     * @param marketMakerId The market maker ID
     * @param sourceSymbol The source symbol
     * @return The mapped symbol or the source symbol if no mapping exists
     */
    public String getProductMapping(String marketMakerId, String sourceSymbol) {
        String key = marketMakerId + ":" + sourceSymbol;
        return productMappingMap.getOrDefault(key, sourceSymbol);
    }

    /**
     * Bulk updates product mappings for a market maker.
     * 
     * @param marketMakerId The market maker ID
     * @param mappings Map of source to mapped symbols
     */
    public void bulkUpdateProductMappings(String marketMakerId, Map<String, String> mappings) {
        Map<String, String> prefixedMappings = new HashMap<>();
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            String key = marketMakerId + ":" + entry.getKey();
            prefixedMappings.put(key, entry.getValue());
        }
        productMappingMap.putAll(prefixedMappings);
        log.info("Bulk updated {} product mappings for {}", mappings.size(), marketMakerId);
    }

    // ==================== Market Data Cache Methods ====================

    /**
     * Caches market data temporarily.
     * Data expires after 1 hour.
     * 
     * @param marketData The market data to cache
     */
    public void cacheMarketData(MarketData marketData) {
        if (marketData == null || marketData.getSymbol() == null) {
            return;
        }
        
        // Store with TTL of 1 hour
        marketDataCache.put(marketData.getSymbol(), marketData, 1, TimeUnit.HOURS);
        log.trace("Cached market data for {}", marketData.getSymbol());
    }

    /**
     * Retrieves cached market data.
     * 
     * @param symbol The symbol to retrieve
     * @return The cached market data or null if not found/expired
     */
    public MarketData getCachedMarketData(String symbol) {
        return marketDataCache.get(symbol);
    }

    /**
     * Retrieves all cached market data.
     * 
     * @return Map of symbol to market data
     */
    public Map<String, MarketData> getAllCachedMarketData() {
        return new HashMap<>(marketDataCache);
    }

    /**
     * Clears the market data cache.
     */
    public void clearMarketDataCache() {
        marketDataCache.clear();
        log.info("Cleared market data cache");
    }

    // ==================== Holiday Cache Methods ====================

    /**
     * Caches holiday information.
     * 
     * @param market The market (e.g., "US", "UK")
     * @param date The date in format "YYYY-MM-DD"
     * @param holidayInfo The holiday information
     */
    public void cacheHoliday(String market, String date, String holidayInfo) {
        String key = market + ":" + date;
        // Cache for 24 hours
        holidayCache.put(key, holidayInfo, 24, TimeUnit.HOURS);
        log.debug("Cached holiday info for {} on {}", market, date);
    }

    /**
     * Retrieves cached holiday information.
     * 
     * @param market The market
     * @param date The date in format "YYYY-MM-DD"
     * @return The holiday information or null if not cached
     */
    public String getCachedHoliday(String market, String date) {
        String key = market + ":" + date;
        return holidayCache.get(key);
    }

    /**
     * Checks if a date is a holiday in a specific market.
     * 
     * @param market The market
     * @param date The date to check
     * @return true if the date is a holiday
     */
    public boolean isHoliday(String market, String date) {
        return getCachedHoliday(market, date) != null;
    }

    // ==================== Utility Methods ====================

    /**
     * Logs statistics about all Hazelcast maps.
     */
    public void logMapStatistics() {
        log.info("Hazelcast Map Statistics:");
        log.info("  Instruments: {} entries", instrumentMap.size());
        log.info("  Market Makers: {} entries", marketMakerMap.size());
        log.info("  Product Mappings: {} entries", productMappingMap.size());
        log.info("  Market Data Cache: {} entries", marketDataCache.size());
        log.info("  Holiday Cache: {} entries", holidayCache.size());
    }

    /**
     * Clears all Hazelcast maps.
     * WARNING: This will remove all distributed configuration!
     */
    public void clearAllMaps() {
        log.warn("Clearing all Hazelcast maps");
        instrumentMap.clear();
        marketMakerMap.clear();
        productMappingMap.clear();
        marketDataCache.clear();
        holidayCache.clear();
        log.warn("All Hazelcast maps cleared");
    }

    /**
     * Checks if Hazelcast is healthy and accessible.
     * 
     * @return true if Hazelcast is healthy
     */
    public boolean isHealthy() {
        try {
            // Try to access each map
            instrumentMap.size();
            marketMakerMap.size();
            productMappingMap.size();
            marketDataCache.size();
            holidayCache.size();
            return true;
        } catch (Exception e) {
            log.error("Hazelcast health check failed", e);
            return false;
        }
    }

    /**
     * Gets the Hazelcast cluster size.
     * 
     * @return Number of members in the cluster
     */
    public int getClusterSize() {
        return hazelcastInstance.getCluster().getMembers().size();
    }

    /**
     * Gets information about the Hazelcast cluster.
     * 
     * @return Cluster information as a string
     */
    public String getClusterInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Hazelcast Cluster Info:\n");
        info.append("  Cluster Size: ").append(getClusterSize()).append("\n");
        info.append("  Local Member: ").append(hazelcastInstance.getCluster().getLocalMember()).append("\n");
        info.append("  All Members: ").append(hazelcastInstance.getCluster().getMembers()).append("\n");
        return info.toString();
    }
}