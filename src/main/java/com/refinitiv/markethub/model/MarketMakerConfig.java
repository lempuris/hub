package com.refinitiv.markethub.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Configuration for market makers (RA, CMF) that connect to the hub.
 * 
 * This configuration defines:
 * - Connection parameters for each market maker
 * - Which instruments they subscribe to
 * - FIX session configuration
 * - Product mappings and transformations
 * 
 * Stored in Hazelcast for distributed access and quick updates.
 * 
 * @author Market Data Hub Team
 * @version 1.0
 */
@Entity
@Table(name = "market_maker_configs",
       indexes = {
           @Index(name = "idx_mm_comp_id", columnList = "compId"),
           @Index(name = "idx_mm_active", columnList = "active")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketMakerConfig implements Serializable {

    /**
     * Unique identifier for the configuration.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Market maker identifier.
     * Example: "RA" for Risk Analytics, "CMF" for Capital Markets Framework
     */
    @Column(nullable = false, unique = true, length = 50)
    private String marketMakerId;

    /**
     * Market maker display name.
     * Example: "Risk Analytics System", "Capital Markets Framework"
     */
    @Column(nullable = false, length = 255)
    private String name;

    /**
     * FIX CompID for this market maker.
     * Used in FIX protocol for identification.
     */
    @Column(name = "comp_id", nullable = false, unique = true, length = 100)
    private String compId;

    /**
     * Type of market maker.
     * Helps categorize different types of systems.
     */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private MarketMakerType type;

    /**
     * Connection mode for this market maker.
     * INITIATOR: Market maker connects to hub
     * ACCEPTOR: Hub connects to market maker
     */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ConnectionMode connectionMode;

    /**
     * Host/IP address for connection.
     * Used when hub is initiator.
     */
    @Column(length = 255)
    private String host;

    /**
     * Port for connection.
     * Used for both acceptor and initiator modes.
     */
    @Column
    private Integer port;

    /**
     * Heartbeat interval in seconds.
     * FIX protocol heartbeat for connection monitoring.
     */
    @Column
    private Integer heartbeatInterval;

    /**
     * List of instrument symbols this market maker subscribes to.
     * Can be specific symbols or patterns like "SPX*" for all SPX options.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "mm_subscriptions", 
                     joinColumns = @JoinColumn(name = "market_maker_id"))
    @Column(name = "symbol_pattern")
    private Set<String> subscribedSymbols;

    /**
     * Product mapping configuration as JSON.
     * Defines how to map hub instruments to market maker's internal codes.
     * Example: {"SPX": "SP500", "AAPL": "AAPL.O"}
     */
    @Column(columnDefinition = "TEXT")
    private String productMappingJson;

    /**
     * Transformation rules as JSON.
     * Defines any data transformations needed for this market maker.
     * Example: {"priceMultiplier": 100, "volumeDivisor": 1000}
     */
    @Column(columnDefinition = "TEXT")
    private String transformationRulesJson;

    /**
     * Additional FIX session settings as JSON.
     * Any extra FIX configuration specific to this market maker.
     */
    @Column(columnDefinition = "TEXT")
    private String fixSettingsJson;

    /**
     * Maximum messages per second this market maker can handle.
     * Used for rate limiting to prevent overwhelming the system.
     */
    @Column
    private Integer maxMessagesPerSecond;

    /**
     * Indicates if this market maker is currently active.
     */
    @Column(nullable = false)
    private Boolean active;

    /**
     * Indicates if this market maker requires authentication.
     */
    @Column(nullable = false)
    private Boolean requiresAuthentication;

    /**
     * Authentication credentials (encrypted).
     * Only used if requiresAuthentication is true.
     */
    @Column
    private String encryptedCredentials;

    /**
     * Priority for message distribution.
     * Higher priority market makers receive data first.
     */
    @Column
    private Integer priority;

    /**
     * Last connection timestamp.
     * Tracks when the market maker last connected.
     */
    @Column
    private LocalDateTime lastConnectionTime;

    /**
     * Connection status.
     * Current status of the connection.
     */
    @Column
    @Enumerated(EnumType.STRING)
    private ConnectionStatus connectionStatus;

    /**
     * Timestamp when the configuration was created.
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when the configuration was last updated.
     */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * User who last updated the configuration.
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
        if (requiresAuthentication == null) {
            requiresAuthentication = false;
        }
        if (heartbeatInterval == null) {
            heartbeatInterval = 30;
        }
        if (priority == null) {
            priority = 0;
        }
        if (connectionStatus == null) {
            connectionStatus = ConnectionStatus.DISCONNECTED;
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
     * Checks if this market maker subscribes to a given symbol.
     * Supports wildcard patterns.
     * 
     * @param symbol The symbol to check
     * @return true if the market maker subscribes to this symbol
     */
    public boolean subscribesTo(String symbol) {
        if (subscribedSymbols == null || subscribedSymbols.isEmpty()) {
            return false;
        }
        
        for (String pattern : subscribedSymbols) {
            if (pattern.equals("*") || pattern.equals(symbol)) {
                return true;
            }
            // Simple wildcard support
            if (pattern.endsWith("*")) {
                String prefix = pattern.substring(0, pattern.length() - 1);
                if (symbol.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Enum defining types of market makers.
     */
    public enum MarketMakerType {
        RISK_ANALYTICS,      // RA system
        CAPITAL_MARKETS,     // CMF system
        PRICING_ENGINE,      // Pricing systems
        EXECUTION_SYSTEM,    // Execution/Trading systems
        OTHER               // Other types
    }

    /**
     * Enum defining connection modes.
     */
    public enum ConnectionMode {
        INITIATOR,  // Market maker connects to hub
        ACCEPTOR    // Hub connects to market maker
    }

    /**
     * Enum defining connection status.
     */
    public enum ConnectionStatus {
        CONNECTED,
        DISCONNECTED,
        CONNECTING,
        ERROR,
        SUSPENDED
    }
}