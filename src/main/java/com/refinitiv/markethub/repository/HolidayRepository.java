package com.refinitiv.markethub.repository;

import com.refinitiv.markethub.model.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for managing holiday calendar data.
 * 
 * Provides database access for holiday information across different markets.
 * 
 * @author Market Data Hub Team
 * @version 1.0
 */
@Repository
public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    /**
     * Finds all holidays for a specific market and year.
     * 
     * @param market The market code (e.g., "US", "UK", "JP")
     * @param year The year
     * @return List of holidays
     */
    @Query("SELECT h FROM Holiday h WHERE h.market = :market AND h.year = :year ORDER BY h.holidayDate")
    List<Holiday> findByMarketAndYear(@Param("market") String market, @Param("year") Integer year);

    /**
     * Finds all holidays for a specific market.
     * 
     * @param market The market code
     * @return List of holidays
     */
    List<Holiday> findByMarketOrderByHolidayDate(String market);

    /**
     * Finds holidays for a specific market within a date range.
     * 
     * @param market The market code
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return List of holidays
     */
    @Query("SELECT h FROM Holiday h WHERE h.market = :market AND h.holidayDate >= :startDate AND h.holidayDate <= :endDate ORDER BY h.holidayDate")
    List<Holiday> findByMarketAndDateRange(@Param("market") String market, 
                                          @Param("startDate") LocalDate startDate, 
                                          @Param("endDate") LocalDate endDate);

    /**
     * Checks if a specific date is a holiday for a market.
     * 
     * @param market The market code
     * @param date The date to check
     * @return true if the date is a holiday
     */
    @Query("SELECT COUNT(h) > 0 FROM Holiday h WHERE h.market = :market AND h.holidayDate = :date")
    boolean isHoliday(@Param("market") String market, @Param("date") LocalDate date);

    /**
     * Finds all distinct markets that have holidays defined.
     * 
     * @return List of market codes
     */
    @Query("SELECT DISTINCT h.market FROM Holiday h ORDER BY h.market")
    List<String> findDistinctMarkets();

    /**
     * Deletes all holidays for a specific market and year.
     * 
     * @param market The market code
     * @param year The year
     * @return Number of deleted records
     */
    @Query("DELETE FROM Holiday h WHERE h.market = :market AND h.year = :year")
    int deleteByMarketAndYear(@Param("market") String market, @Param("year") Integer year);
}