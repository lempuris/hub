package com.refinitiv.markethub.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a financial instrument in the Market Data Hub.
 * 
 * This model represents instruments that are:
 * - Loaded end-of-day (EOD) initially
 * - Already traded with a closing price
 * - Primarily from EDM (Equity Derivatives Market)
 * 
 * The instrument configuration is designed to be flexible and configurable
 * without code changes, supporting the goal of faster time-to-market.
 * 
 * @author Market Data Hub Team
 * @version 1.0
 */
@Entity
@Table(name = "instruments", indexes = {
    @Index(name = "idx_instrument_symbol", columnList = "symbol"),
    @Index(name = "idx_instrument_type", columnList = "instrumentType"),
    @Index(name = "idx_instrument_active", columnList = "active")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Instrument implements Serializable {

    /**
     * Unique identifier for the instrument.
     * This is the internal ID used by the hub.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Trading symbol or ticker for the instrument.
     * Example: "AAPL", "GOOGL", "SPX"
     */
    @Column(nullable = false, unique = true, length = 50)
    private String symbol;

    /**
     * Full name or description of the instrument.
     * Example: "Apple Inc.", "S&P 500 Index"
     */
    @Column(nullable = false, length = 255)
    private String description;

    /**
     * Type of instrument.
     * Examples: EQUITY, OPTION, FUTURE, INDEX
     */
    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private InstrumentType instrumentType;

    /**
     * Exchange where the instrument is traded.
     * Example: "NYSE", "NASDAQ", "CBOE"
     */
    @Column(length = 50)
    private String exchange;

    /**
     * Currency in which the instrument is denominated.
     * Example: "USD", "EUR", "GBP"
     */
    @Column(nullable = false, length = 3)
    private String currency;

    /**
     * Last closing price of the instrument.
     * Required for market making as per requirements.
     */
    @Column(precision = 19, scale = 6)
    private BigDecimal closingPrice;

    /**
     * Date of the last closing price.
     */
    @Column
    private LocalDate closingPriceDate;

    /**
     * Minimum price movement for the instrument.
     * Used for price calculations and rounding.
     */
    @Column(precision = 19, scale = 6)
    private BigDecimal tickSize;

    /**
     * Contract size for derivatives.
     * For equities, this is typically 1.
     */
    @Column
    private Integer contractSize;

    /**
     * Expiry date for derivatives.
     * Null for equities and perpetual instruments.
     */
    @Column
    private LocalDate expiryDate;

    /**
     * Strike price for options.
     * Null for non-option instruments.
     */
    @Column(precision = 19, scale = 6)
    private BigDecimal strikePrice;

    /**
     * Option type for options.
     * CALL or PUT, null for non-options.
     */
    @Column(length = 10)
    private String optionType;

    /**
     * Underlying instrument symbol for derivatives.
     * Example: "AAPL" for AAPL options
     */
    @Column(length = 50)
    private String underlyingSymbol;

    /**
     * Indicates if the instrument is currently active for trading.
     */
    @Column(nullable = false)
    private Boolean active;

    /**
     * Refinitiv RIC (Reuters Instrument Code) for the instrument.
     * Used for mapping to Refinitiv data feeds.
     */
    @Column(unique = true, length = 100)
    private String refinitivRic;

    /**
     * Configuration JSON for instrument-specific settings.
     * Allows flexible configuration without code changes.
     */
    @Column(columnDefinition = "TEXT")
    private String configurationJson;

    /**
     * Timestamp when the instrument was created in the system.
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when the instrument was last updated.
     */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * User who last updated the instrument configuration.
     */
    @Column(length = 100)
    private String updatedBy;

    /**
     * Pre-persist method to set timestamps before initial save.
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (active == null) {
            active = true;
        }
        if (contractSize == null) {
            contractSize = 1;
        }
    }

    /**
     * Pre-update method to update timestamp before updates.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Enum defining the types of instruments supported.
     */
    public enum InstrumentType {
        EQUITY,      // Stocks/Shares
        OPTION,      // Options contracts
        FUTURE,      // Futures contracts
        INDEX,       // Market indices
        ETF,         // Exchange-traded funds
        WARRANT,     // Warrants
        BOND,        // Fixed income
        COMMODITY    // Commodity instruments
    }
}