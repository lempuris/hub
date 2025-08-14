package com.refinitiv.markethub.event;

import com.refinitiv.markethub.model.MarketData;
import org.springframework.context.ApplicationEvent;

/**
 * Event published when new market data is available for distribution.
 * This decouples the MarketDataService from the FIX application,
 * avoiding circular dependencies.
 * 
 * @author Market Data Hub Team
 * @version 1.0
 */
public class MarketDataEvent extends ApplicationEvent {
    
    private final MarketData marketData;
    
    /**
     * Creates a new market data event.
     * 
     * @param source The object that published the event
     * @param marketData The market data to distribute
     */
    public MarketDataEvent(Object source, MarketData marketData) {
        super(source);
        this.marketData = marketData;
    }
    
    /**
     * Gets the market data associated with this event.
     * 
     * @return The market data
     */
    public MarketData getMarketData() {
        return marketData;
    }
}