package com.refinitiv.markethub.repository;

import com.refinitiv.markethub.model.MarketMakerConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for MarketMakerConfig entity.
 * 
 * Provides database access methods for market maker configurations.
 * Spring Data JPA automatically implements these methods at runtime.
 * 
 * @author Market Data Hub Team
 * @version 1.0
 */
@Repository
public interface MarketMakerConfigRepository extends JpaRepository<MarketMakerConfig, Long> {

    /**
     * Finds a market maker configuration by its market maker ID.
     * 
     * @param marketMakerId The unique market maker identifier
     * @return The configuration or null if not found
     */
    MarketMakerConfig findByMarketMakerId(String marketMakerId);

    /**
     * Finds a market maker configuration by its FIX CompID.
     * 
     * @param compId The FIX CompID
     * @return The configuration or null if not found
     */
    MarketMakerConfig findByCompId(String compId);

    /**
     * Finds all market maker configurations with a specific active status.
     * 
     * @param active The active status to filter by
     * @return List of configurations matching the active status
     */
    List<MarketMakerConfig> findByActive(Boolean active);

    /**
     * Finds all market maker configurations with a specific connection status.
     * 
     * @param connectionStatus The connection status to filter by
     * @return List of configurations matching the connection status
     */
    List<MarketMakerConfig> findByConnectionStatus(MarketMakerConfig.ConnectionStatus connectionStatus);

    /**
     * Finds all market maker configurations of a specific type.
     * 
     * @param type The market maker type
     * @return List of configurations of the specified type
     */
    List<MarketMakerConfig> findByType(MarketMakerConfig.MarketMakerType type);

    /**
     * Finds all active market maker configurations of a specific type.
     * 
     * @param type The market maker type
     * @param active The active status
     * @return List of active configurations of the specified type
     */
    List<MarketMakerConfig> findByTypeAndActive(MarketMakerConfig.MarketMakerType type, Boolean active);

    /**
     * Finds all market maker configurations ordered by priority (highest first).
     * 
     * @return List of configurations ordered by priority descending
     */
    List<MarketMakerConfig> findAllByOrderByPriorityDesc();

    /**
     * Finds all active market maker configurations ordered by priority.
     * 
     * @param active The active status
     * @return List of active configurations ordered by priority descending
     */
    List<MarketMakerConfig> findByActiveOrderByPriorityDesc(Boolean active);

    /**
     * Checks if a market maker with the given CompID exists.
     * 
     * @param compId The FIX CompID to check
     * @return true if exists, false otherwise
     */
    boolean existsByCompId(String compId);

    /**
     * Checks if a market maker with the given market maker ID exists.
     * 
     * @param marketMakerId The market maker ID to check
     * @return true if exists, false otherwise
     */
    boolean existsByMarketMakerId(String marketMakerId);

    /**
     * Finds market makers that subscribe to a specific symbol.
     * This is a custom query that checks if the symbol is in the subscribed symbols set.
     * 
     * @param symbol The symbol to check
     * @return List of market makers that subscribe to the symbol
     */
    @Query("SELECT m FROM MarketMakerConfig m JOIN m.subscribedSymbols s WHERE s = :symbol AND m.active = true")
    List<MarketMakerConfig> findActiveSubscribersForSymbol(@Param("symbol") String symbol);

    /**
     * Finds market makers that subscribe to symbols matching a pattern.
     * This query supports wildcard patterns like 'SPX*'.
     * 
     * @param pattern The pattern to match
     * @return List of market makers with matching subscriptions
     */
    @Query("SELECT m FROM MarketMakerConfig m JOIN m.subscribedSymbols s WHERE s LIKE :pattern AND m.active = true")
    List<MarketMakerConfig> findActiveSubscribersForPattern(@Param("pattern") String pattern);

    /**
     * Counts the number of active market makers.
     * 
     * @return Count of active market makers
     */
    long countByActive(Boolean active);

    /**
     * Counts the number of connected market makers.
     * 
     * @return Count of connected market makers
     */
    long countByConnectionStatus(MarketMakerConfig.ConnectionStatus connectionStatus);
}