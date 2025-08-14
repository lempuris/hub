package com.refinitiv.markethub.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents real-time market data for an instrument.
 * 
 * This is a transient model used for:
 * - Receiving data from Refinitiv
 * - Distributing data to market makers (RA, CMF)
 * - Temporary caching in Hazelcast (1 day retention for debugging)
 * 
 * Market data is NOT persisted long-term as per V1 requirements.
 * It acts as a pass-through from Refinitiv to market makers.
 * 
 * @author Market Data Hub Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketData implements Serializable {

    /**
     * Symbol of the instrument this market data is for.
     * Maps to Instrument.symbol
     */
    private String symbol;

    /**
     * Refinitiv RIC (Reuters Instrument Code).
     * Used for mapping from Refinitiv data feed.
     */
    private String ric;

    /**
     * Current bid price.
     * Best price a buyer is willing to pay.
     */
    private BigDecimal bidPrice;

    /**
     * Current ask price.
     * Best price a seller is willing to accept.
     */
    private BigDecimal askPrice;

    /**
     * Size/volume available at the bid price.
     */
    private Long bidSize;

    /**
     * Size/volume available at the ask price.
     */
    private Long askSize;

    /**
     * Last traded price.
     */
    private BigDecimal lastPrice;

    /**
     * Size/volume of the last trade.
     */
    private Long lastSize;

    /**
     * Mid price calculated as (bid + ask) / 2.
     * Used for various calculations and interpolation.
     */
    private BigDecimal midPrice;

    /**
     * Daily high price.
     */
    private BigDecimal highPrice;

    /**
     * Daily low price.
     */
    private BigDecimal lowPrice;

    /**
     * Opening price for the day.
     */
    private BigDecimal openPrice;

    /**
     * Previous closing price.
     * Used for change calculations.
     */
    private BigDecimal previousClose;

    /**
     * Total volume traded today.
     */
    private Long volume;

    /**
     * Timestamp when this market data was received from Refinitiv.
     */
    private LocalDateTime sourceTimestamp;

    /**
     * Timestamp when this market data was processed by the hub.
     */
    private LocalDateTime processedTimestamp;

    /**
     * Timestamp when this market data was sent to market makers.
     */
    private LocalDateTime distributedTimestamp;

    /**
     * Source of the market data.
     * Example: "REFINITIV", "CALCULATED"
     */
    private String source;

    /**
     * Quality indicator for the data.
     * Can be used to indicate stale, calculated, or live data.
     */
    private DataQuality quality;

    /**
     * Sequence number for ordering and gap detection.
     * Helps identify missing messages.
     */
    private Long sequenceNumber;

    /**
     * Additional fields as JSON for flexibility.
     * Allows passing through additional Refinitiv fields without schema changes.
     */
    private String additionalFieldsJson;

    /**
     * Calculates the mid price if not already set.
     * Mid price = (bid + ask) / 2
     */
    public void calculateMidPrice() {
        if (bidPrice != null && askPrice != null) {
            this.midPrice = bidPrice.add(askPrice).divide(BigDecimal.valueOf(2));
        }
    }

    /**
     * Calculates the spread.
     * Spread = ask - bid
     * 
     * @return The spread or null if prices not available
     */
    public BigDecimal getSpread() {
        if (bidPrice != null && askPrice != null) {
            return askPrice.subtract(bidPrice);
        }
        return null;
    }

    /**
     * Calculates the daily change.
     * Change = last - previousClose
     * 
     * @return The daily change or null if prices not available
     */
    public BigDecimal getDailyChange() {
        if (lastPrice != null && previousClose != null) {
            return lastPrice.subtract(previousClose);
        }
        return null;
    }

    /**
     * Calculates the daily change percentage.
     * Change% = (last - previousClose) / previousClose * 100
     * 
     * @return The daily change percentage or null if prices not available
     */
    public BigDecimal getDailyChangePercent() {
        if (lastPrice != null && previousClose != null && 
            previousClose.compareTo(BigDecimal.ZERO) != 0) {
            return lastPrice.subtract(previousClose)
                .divide(previousClose, 4, BigDecimal.ROUND_HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        }
        return null;
    }

    /**
     * Enum defining data quality levels.
     */
    public enum DataQuality {
        LIVE,        // Real-time live data
        DELAYED,     // Delayed data (e.g., 15 min delay)
        STALE,       // Data older than acceptable threshold
        CALCULATED,  // Derived or interpolated data
        INDICATIVE,  // Indicative pricing only
        CLOSED       // Market closed, showing closing prices
    }
}