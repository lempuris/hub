package com.refinitiv.markethub.service;

import com.refinitiv.markethub.event.MarketDataEvent;
import com.refinitiv.markethub.model.Instrument;
import com.refinitiv.markethub.model.MarketData;
import com.refinitiv.markethub.model.MarketMakerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for managing market data flow through the hub.
 * 
 * This service:
 * - Receives market data from Refinitiv (via ETA integration)
 * - Validates and enriches market data
 * - Distributes data to subscribed market makers via FIX
 * - Manages data caching for debugging
 * - Handles data quality and staleness checks
 * 
 * Acts as the central hub for all market data processing.
 * 
 * @author Market Data Hub Team
 * @version 1.0
 */
@Service
@Slf4j
public class MarketDataService {

    @Autowired
    private HazelcastConfigService hazelcastService;


    @Autowired
    private MarketMakerService marketMakerService;
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Value("${markethub.market-data.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${markethub.market-data.cache.ttl-minutes:60}")
    private int cacheTtlMinutes;

    /**
     * Local cache for latest market data per symbol.
     * Used for quick lookups and snapshot requests.
     */
    private final Map<String, MarketData> latestDataCache = new ConcurrentHashMap<>();

    /**
     * Statistics counters for monitoring.
     */
    private final AtomicLong messagesReceived = new AtomicLong(0);
    private final AtomicLong messagesDistributed = new AtomicLong(0);
    private final AtomicLong messagesDropped = new AtomicLong(0);

    /**
     * Initializes the market data service.
     */
    @PostConstruct
    public void initialize() {
        log.info("Initializing Market Data Service");
        log.info("Cache enabled: {}, TTL: {} minutes", cacheEnabled, cacheTtlMinutes);
    }

    /**
     * Processes incoming market data from Refinitiv.
     * This is the main entry point for market data into the hub.
     * 
     * @param marketData The incoming market data
     */
    @Async("marketDataExecutor")
    public void processIncomingMarketData(MarketData marketData) {
        if (marketData == null || marketData.getSymbol() == null) {
            log.warn("Received null or invalid market data");
            messagesDropped.incrementAndGet();
            return;
        }

        messagesReceived.incrementAndGet();
        
        try {
            // Log receipt of data
            log.debug("Processing market data for symbol: {}", marketData.getSymbol());
            
            // Set processing timestamp
            marketData.setProcessedTimestamp(LocalDateTime.now());
            
            // Validate market data
            if (!validateMarketData(marketData)) {
                log.warn("Market data validation failed for {}", marketData.getSymbol());
                messagesDropped.incrementAndGet();
                return;
            }
            
            // Enrich market data
            enrichMarketData(marketData);
            
            // Check if instrument is configured
            Instrument instrument = hazelcastService.getInstrument(marketData.getSymbol());
            if (instrument == null) {
                log.debug("No instrument configuration found for {}", marketData.getSymbol());
                // Still process if no configuration - pass through mode
            } else if (!instrument.getActive()) {
                log.debug("Instrument {} is not active, dropping market data", marketData.getSymbol());
                messagesDropped.incrementAndGet();
                return;
            }
            
            // Update local cache
            latestDataCache.put(marketData.getSymbol(), marketData);
            
            // Cache in Hazelcast if enabled
            if (cacheEnabled) {
                hazelcastService.cacheMarketData(marketData);
            }
            
            // Distribute to market makers
            distributeMarketData(marketData);
            
            messagesDistributed.incrementAndGet();
            
        } catch (Exception e) {
            log.error("Error processing market data for " + marketData.getSymbol(), e);
            messagesDropped.incrementAndGet();
        }
    }

    /**
     * Validates incoming market data.
     * Checks for required fields and data quality.
     * 
     * @param marketData The market data to validate
     * @return true if valid, false otherwise
     */
    private boolean validateMarketData(MarketData marketData) {
        // Basic validation - at least one price should be present
        boolean hasPrice = marketData.getBidPrice() != null ||
                          marketData.getAskPrice() != null ||
                          marketData.getLastPrice() != null;
        
        if (!hasPrice) {
            log.warn("Market data for {} has no prices", marketData.getSymbol());
            return false;
        }
        
        // Validate bid/ask spread if both are present
        if (marketData.getBidPrice() != null && marketData.getAskPrice() != null) {
            BigDecimal spread = marketData.getAskPrice().subtract(marketData.getBidPrice());
            if (spread.compareTo(BigDecimal.ZERO) < 0) {
                log.warn("Invalid spread for {}: bid {} > ask {}", 
                        marketData.getSymbol(), marketData.getBidPrice(), marketData.getAskPrice());
                return false;
            }
            
            // Check for excessive spread (possible bad data)
            BigDecimal midPrice = marketData.getBidPrice().add(marketData.getAskPrice())
                                   .divide(BigDecimal.valueOf(2));
            BigDecimal spreadPercent = spread.divide(midPrice, 4, BigDecimal.ROUND_HALF_UP)
                                            .multiply(BigDecimal.valueOf(100));
            
            if (spreadPercent.compareTo(BigDecimal.valueOf(10)) > 0) {
                log.warn("Excessive spread {}% for {}", spreadPercent, marketData.getSymbol());
                // Don't reject, just warn
            }
        }
        
        // Validate sizes if present
        if (marketData.getBidSize() != null && marketData.getBidSize() < 0) {
            log.warn("Invalid bid size for {}: {}", marketData.getSymbol(), marketData.getBidSize());
            return false;
        }
        
        if (marketData.getAskSize() != null && marketData.getAskSize() < 0) {
            log.warn("Invalid ask size for {}: {}", marketData.getSymbol(), marketData.getAskSize());
            return false;
        }
        
        return true;
    }

    /**
     * Enriches market data with calculated fields.
     * 
     * @param marketData The market data to enrich
     */
    private void enrichMarketData(MarketData marketData) {
        // Calculate mid price if not set
        if (marketData.getMidPrice() == null) {
            marketData.calculateMidPrice();
        }
        
        // Set data quality based on timestamp
        if (marketData.getSourceTimestamp() != null) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime sourceTime = marketData.getSourceTimestamp();
            
            long minutesOld = java.time.Duration.between(sourceTime, now).toMinutes();
            
            if (minutesOld < 1) {
                marketData.setQuality(MarketData.DataQuality.LIVE);
            } else if (minutesOld < 15) {
                marketData.setQuality(MarketData.DataQuality.DELAYED);
            } else if (minutesOld < 60) {
                marketData.setQuality(MarketData.DataQuality.STALE);
            } else {
                marketData.setQuality(MarketData.DataQuality.CLOSED);
            }
        } else {
            marketData.setQuality(MarketData.DataQuality.INDICATIVE);
        }
        
        // Set source if not set
        if (marketData.getSource() == null) {
            marketData.setSource("REFINITIV");
        }
    }

    /**
     * Distributes market data to subscribed market makers.
     * 
     * @param marketData The market data to distribute
     */
    private void distributeMarketData(MarketData marketData) {
        // Set distribution timestamp
        marketData.setDistributedTimestamp(LocalDateTime.now());
        
        // Get all active market makers
        for (MarketMakerConfig config : marketMakerService.getActiveMarketMakers()) {
            // Check if this market maker subscribes to this symbol
            if (config.subscribesTo(marketData.getSymbol())) {
                // Apply product mapping if configured
                String mappedSymbol = hazelcastService.getProductMapping(
                    config.getMarketMakerId(), marketData.getSymbol());
                
                if (!mappedSymbol.equals(marketData.getSymbol())) {
                    log.debug("Mapping symbol {} to {} for {}", 
                            marketData.getSymbol(), mappedSymbol, config.getName());
                    
                    // Create a copy with mapped symbol
                    MarketData mappedData = copyMarketData(marketData);
                    mappedData.setSymbol(mappedSymbol);
                    
                    // Send via FIX if connected
                    sendToMarketMaker(config, mappedData);
                } else {
                    // Send original data
                    sendToMarketMaker(config, marketData);
                }
            }
        }
        
        // Publish event for FIX distribution
        eventPublisher.publishEvent(new MarketDataEvent(this, marketData));
    }

    /**
     * Sends market data to a specific market maker.
     * 
     * @param config The market maker configuration
     * @param marketData The market data to send
     */
    private void sendToMarketMaker(MarketMakerConfig config, MarketData marketData) {
        // Check connection status
        if (config.getConnectionStatus() != MarketMakerConfig.ConnectionStatus.CONNECTED) {
            log.debug("Market maker {} is not connected, skipping", config.getName());
            return;
        }
        
        // Apply rate limiting if configured
        if (config.getMaxMessagesPerSecond() != null) {
            // Simple rate limiting - in production would use a more sophisticated approach
            // For now, just log
            log.trace("Sending to {} (rate limit: {} msg/sec)", 
                    config.getName(), config.getMaxMessagesPerSecond());
        }
        
        // Send via appropriate channel (FIX for now)
        // The FIX application handles the actual sending
        log.debug("Market data for {} queued for delivery to {}", 
                marketData.getSymbol(), config.getName());
    }

    /**
     * Creates a copy of market data.
     * 
     * @param original The original market data
     * @return A copy of the market data
     */
    private MarketData copyMarketData(MarketData original) {
        return MarketData.builder()
            .symbol(original.getSymbol())
            .ric(original.getRic())
            .bidPrice(original.getBidPrice())
            .askPrice(original.getAskPrice())
            .bidSize(original.getBidSize())
            .askSize(original.getAskSize())
            .lastPrice(original.getLastPrice())
            .lastSize(original.getLastSize())
            .midPrice(original.getMidPrice())
            .highPrice(original.getHighPrice())
            .lowPrice(original.getLowPrice())
            .openPrice(original.getOpenPrice())
            .previousClose(original.getPreviousClose())
            .volume(original.getVolume())
            .sourceTimestamp(original.getSourceTimestamp())
            .processedTimestamp(original.getProcessedTimestamp())
            .distributedTimestamp(original.getDistributedTimestamp())
            .source(original.getSource())
            .quality(original.getQuality())
            .sequenceNumber(original.getSequenceNumber())
            .additionalFieldsJson(original.getAdditionalFieldsJson())
            .build();
    }

    /**
     * Gets the latest market data for a symbol.
     * 
     * @param symbol The symbol to get data for
     * @return The latest market data or null if not available
     */
    public MarketData getLatestMarketData(String symbol) {
        // First check local cache
        MarketData data = latestDataCache.get(symbol);
        
        // If not in local cache, check Hazelcast
        if (data == null && cacheEnabled) {
            data = hazelcastService.getCachedMarketData(symbol);
        }
        
        return data;
    }

    /**
     * Gets all cached market data.
     * 
     * @return Map of symbol to market data
     */
    public Map<String, MarketData> getAllCachedMarketData() {
        return new ConcurrentHashMap<>(latestDataCache);
    }

    /**
     * Clears the local market data cache.
     */
    public void clearLocalCache() {
        latestDataCache.clear();
        log.info("Cleared local market data cache");
    }

    /**
     * Gets service statistics.
     * 
     * @return Statistics as a formatted string
     */
    public String getStatistics() {
        return String.format(
            "Market Data Service Statistics:\n" +
            "  Messages Received: %d\n" +
            "  Messages Distributed: %d\n" +
            "  Messages Dropped: %d\n" +
            "  Symbols Cached: %d\n" +
            "  Cache Hit Rate: %.2f%%",
            messagesReceived.get(),
            messagesDistributed.get(),
            messagesDropped.get(),
            latestDataCache.size(),
            calculateCacheHitRate()
        );
    }

    /**
     * Calculates the cache hit rate.
     * 
     * @return The cache hit rate as a percentage
     */
    private double calculateCacheHitRate() {
        long total = messagesReceived.get();
        if (total == 0) return 0.0;
        
        long hits = messagesDistributed.get();
        return (double) hits / total * 100.0;
    }

    /**
     * Resets service statistics.
     */
    public void resetStatistics() {
        messagesReceived.set(0);
        messagesDistributed.set(0);
        messagesDropped.set(0);
        log.info("Reset market data service statistics");
    }

    /**
     * Simulates market data for testing purposes.
     * 
     * @param symbol The symbol to generate data for
     * @return Generated market data
     */
    public MarketData generateTestMarketData(String symbol) {
        BigDecimal basePrice = BigDecimal.valueOf(100 + Math.random() * 900);
        BigDecimal spread = basePrice.multiply(BigDecimal.valueOf(0.001)); // 0.1% spread
        
        MarketData testData = MarketData.builder()
            .symbol(symbol)
            .bidPrice(basePrice.subtract(spread.divide(BigDecimal.valueOf(2))))
            .askPrice(basePrice.add(spread.divide(BigDecimal.valueOf(2))))
            .bidSize((long) (100 + Math.random() * 900))
            .askSize((long) (100 + Math.random() * 900))
            .lastPrice(basePrice)
            .lastSize((long) (100 + Math.random() * 500))
            .volume((long) (10000 + Math.random() * 90000))
            .sourceTimestamp(LocalDateTime.now())
            .source("TEST")
            .quality(MarketData.DataQuality.LIVE)
            .build();
        
        testData.calculateMidPrice();
        
        log.debug("Generated test market data for {}", symbol);
        return testData;
    }
}