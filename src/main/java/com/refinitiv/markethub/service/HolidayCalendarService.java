package com.refinitiv.markethub.service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.refinitiv.markethub.model.Holiday;
import com.refinitiv.markethub.repository.HolidayRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for managing holiday calendars across different markets.
 * 
 * This service:
 * - Maintains holiday calendars for multiple markets
 * - Determines if a given date is a trading day
 * - Calculates next/previous trading days
 * - Supports different market holiday schedules (US, UK, JP, etc.)
 * - Caches holiday data in Hazelcast for performance
 * 
 * The service is crucial for:
 * - Determining when markets are open
 * - Calculating settlement dates
 * - Adjusting for market holidays in pricing calculations
 * 
 * Assigned to: Benefactor (as per requirements)
 * 
 * @author Market Data Hub Team - Benefactor
 * @version 1.0
 */
@Service
@Slf4j
public class HolidayCalendarService {

    @Autowired
    private HolidayRepository holidayRepository;

    @Autowired
    private HazelcastInstance hazelcastInstance;

    /**
     * Hazelcast map for caching holidays by market and year.
     * Key format: "MARKET:YEAR" (e.g., "US:2024")
     * Value: Set of LocalDate holidays
     */
    private IMap<String, Set<LocalDate>> holidayCache;

    /**
     * Local cache for weekend days by market.
     * Some markets have different weekend days (e.g., Middle East)
     */
    private final Map<String, Set<DayOfWeek>> marketWeekends = new ConcurrentHashMap<>();

    /**
     * Supported market codes.
     */
    public enum MarketCode {
        US("United States"),
        UK("United Kingdom"),
        JP("Japan"),
        EU("European Union"),
        HK("Hong Kong"),
        SG("Singapore"),
        AU("Australia"),
        CA("Canada"),
        CH("Switzerland"),
        AE("United Arab Emirates"),
        SA("Saudi Arabia");

        private final String description;

        MarketCode(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Initializes the holiday calendar service.
     */
    @PostConstruct
    public void initialize() {
        log.info("Initializing Holiday Calendar Service");
        
        // Initialize Hazelcast cache
        holidayCache = hazelcastInstance.getMap("holidayCalendar");
        
        // Initialize weekend days for different markets
        initializeMarketWeekends();
        
        // Load holidays from database
        loadHolidaysIntoCache();
        
        // Initialize with some default holidays if database is empty
        if (holidayRepository.count() == 0) {
            initializeDefaultHolidays();
        }
        
        log.info("Holiday Calendar Service initialized with {} markets", marketWeekends.size());
    }

    /**
     * Initializes weekend days for different markets.
     * Most markets use Saturday/Sunday, but some differ.
     */
    private void initializeMarketWeekends() {
        // Most markets use Saturday and Sunday
        Set<DayOfWeek> standardWeekend = Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        
        marketWeekends.put("US", standardWeekend);
        marketWeekends.put("UK", standardWeekend);
        marketWeekends.put("JP", standardWeekend);
        marketWeekends.put("EU", standardWeekend);
        marketWeekends.put("HK", standardWeekend);
        marketWeekends.put("SG", standardWeekend);
        marketWeekends.put("AU", standardWeekend);
        marketWeekends.put("CA", standardWeekend);
        marketWeekends.put("CH", standardWeekend);
        
        // Middle East markets typically use Friday/Saturday
        Set<DayOfWeek> middleEastWeekend = Set.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);
        marketWeekends.put("AE", middleEastWeekend);
        marketWeekends.put("SA", middleEastWeekend);
    }

    /**
     * Loads holidays from database into Hazelcast cache.
     */
    private void loadHolidaysIntoCache() {
        List<Holiday> allHolidays = holidayRepository.findAll();
        
        // Group holidays by market and year
        Map<String, Set<LocalDate>> groupedHolidays = allHolidays.stream()
            .collect(Collectors.groupingBy(
                h -> h.getMarket() + ":" + h.getYear(),
                Collectors.mapping(Holiday::getHolidayDate, Collectors.toSet())
            ));
        
        // Put all into cache
        holidayCache.putAll(groupedHolidays);
        
        log.info("Loaded {} holiday entries into cache", groupedHolidays.size());
    }

    /**
     * Initializes default holidays for major markets.
     * This provides a basic set of holidays for testing.
     */
    private void initializeDefaultHolidays() {
        log.info("Initializing default holidays for major markets");
        
        int currentYear = LocalDate.now().getYear();
        
        // US Holidays
        addHoliday("US", currentYear, 1, 1, "New Year's Day");
        addHoliday("US", currentYear, 1, 15, "Martin Luther King Jr. Day");  // 3rd Monday in January
        addHoliday("US", currentYear, 2, 19, "Presidents' Day");  // 3rd Monday in February
        addHoliday("US", currentYear, 5, 27, "Memorial Day");  // Last Monday in May
        addHoliday("US", currentYear, 7, 4, "Independence Day");
        addHoliday("US", currentYear, 9, 2, "Labor Day");  // 1st Monday in September
        addHoliday("US", currentYear, 11, 28, "Thanksgiving");  // 4th Thursday in November
        addHoliday("US", currentYear, 12, 25, "Christmas Day");
        
        // UK Holidays
        addHoliday("UK", currentYear, 1, 1, "New Year's Day");
        addHoliday("UK", currentYear, 4, 7, "Good Friday");  // Variable
        addHoliday("UK", currentYear, 4, 10, "Easter Monday");  // Variable
        addHoliday("UK", currentYear, 5, 6, "Early May Bank Holiday");  // 1st Monday in May
        addHoliday("UK", currentYear, 5, 27, "Spring Bank Holiday");  // Last Monday in May
        addHoliday("UK", currentYear, 8, 26, "Summer Bank Holiday");  // Last Monday in August
        addHoliday("UK", currentYear, 12, 25, "Christmas Day");
        addHoliday("UK", currentYear, 12, 26, "Boxing Day");
        
        // Japan Holidays (Golden Week and others)
        addHoliday("JP", currentYear, 1, 1, "New Year's Day");
        addHoliday("JP", currentYear, 1, 2, "Bank Holiday");
        addHoliday("JP", currentYear, 1, 3, "Bank Holiday");
        addHoliday("JP", currentYear, 2, 11, "National Foundation Day");
        addHoliday("JP", currentYear, 2, 23, "Emperor's Birthday");
        addHoliday("JP", currentYear, 4, 29, "Showa Day");
        addHoliday("JP", currentYear, 5, 3, "Constitution Memorial Day");
        addHoliday("JP", currentYear, 5, 4, "Greenery Day");
        addHoliday("JP", currentYear, 5, 5, "Children's Day");
        
        log.info("Default holidays initialized");
    }

    /**
     * Adds a holiday to the database and cache.
     */
    private void addHoliday(String market, int year, int month, int day, String description) {
        Holiday holiday = new Holiday();
        holiday.setMarket(market);
        holiday.setYear(year);
        holiday.setHolidayDate(LocalDate.of(year, month, day));
        holiday.setHolidayName(description);
        holiday.setHolidayType(Holiday.HolidayType.PUBLIC); // Set default holiday type
        holiday.setIsFullDay(true); // Set default full day
        holiday.setActive(true); // Set active
        holiday.setCreatedAt(LocalDateTime.now());
        holiday.setUpdatedAt(LocalDateTime.now());
        
        holidayRepository.save(holiday);
        
        // Update cache
        String cacheKey = market + ":" + year;
        Set<LocalDate> holidays = holidayCache.getOrDefault(cacheKey, new HashSet<>());
        holidays.add(holiday.getHolidayDate());
        holidayCache.put(cacheKey, holidays);
    }

    // ==================== Public API ====================

    /**
     * Checks if a given date is a trading day for a specific market.
     * 
     * @param market The market code
     * @param date The date to check
     * @return true if it's a trading day, false if it's a holiday or weekend
     */
    public boolean isTradingDay(String market, LocalDate date) {
        // Check if it's a weekend for this market
        if (isWeekend(market, date)) {
            return false;
        }
        
        // Check if it's a holiday
        if (isHoliday(market, date)) {
            return false;
        }
        
        return true;
    }

    /**
     * Checks if a date is a weekend for a specific market.
     * 
     * @param market The market code
     * @param date The date to check
     * @return true if it's a weekend day for this market
     */
    public boolean isWeekend(String market, LocalDate date) {
        Set<DayOfWeek> weekends = marketWeekends.getOrDefault(market, 
            Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));
        return weekends.contains(date.getDayOfWeek());
    }

    /**
     * Checks if a date is a holiday for a specific market.
     * 
     * @param market The market code
     * @param date The date to check
     * @return true if it's a holiday
     */
    public boolean isHoliday(String market, LocalDate date) {
        String cacheKey = market + ":" + date.getYear();
        Set<LocalDate> holidays = holidayCache.get(cacheKey);
        
        if (holidays == null) {
            // Try to load from database if not in cache
            List<Holiday> dbHolidays = holidayRepository.findByMarketAndYear(market, date.getYear());
            holidays = dbHolidays.stream()
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toSet());
            holidayCache.put(cacheKey, holidays);
        }
        
        return holidays != null && holidays.contains(date);
    }

    /**
     * Gets the next trading day for a market.
     * 
     * @param market The market code
     * @param date The starting date
     * @return The next trading day
     */
    public LocalDate getNextTradingDay(String market, LocalDate date) {
        LocalDate nextDay = date.plusDays(1);
        
        while (!isTradingDay(market, nextDay)) {
            nextDay = nextDay.plusDays(1);
        }
        
        return nextDay;
    }

    /**
     * Gets the previous trading day for a market.
     * 
     * @param market The market code
     * @param date The starting date
     * @return The previous trading day
     */
    public LocalDate getPreviousTradingDay(String market, LocalDate date) {
        LocalDate prevDay = date.minusDays(1);
        
        while (!isTradingDay(market, prevDay)) {
            prevDay = prevDay.minusDays(1);
        }
        
        return prevDay;
    }

    /**
     * Calculates the number of trading days between two dates.
     * 
     * @param market The market code
     * @param startDate The start date (inclusive)
     * @param endDate The end date (inclusive)
     * @return The number of trading days
     */
    public int getTradingDaysBetween(String market, LocalDate startDate, LocalDate endDate) {
        int count = 0;
        LocalDate current = startDate;
        
        while (!current.isAfter(endDate)) {
            if (isTradingDay(market, current)) {
                count++;
            }
            current = current.plusDays(1);
        }
        
        return count;
    }

    /**
     * Gets all holidays for a market and year.
     * 
     * @param market The market code
     * @param year The year
     * @return List of holidays
     */
    public List<Holiday> getHolidays(String market, int year) {
        return holidayRepository.findByMarketAndYear(market, year);
    }

    /**
     * Adds a new holiday.
     * 
     * @param holiday The holiday to add
     * @return The saved holiday
     */
    public Holiday addHoliday(Holiday holiday) {
        // Set defaults if not provided
        if (holiday.getHolidayType() == null) {
            holiday.setHolidayType(Holiday.HolidayType.PUBLIC);
        }
        if (holiday.getIsFullDay() == null) {
            holiday.setIsFullDay(true);
        }
        if (holiday.getActive() == null) {
            holiday.setActive(true);
        }
        holiday.setCreatedAt(LocalDateTime.now());
        holiday.setUpdatedAt(LocalDateTime.now());
        
        Holiday saved = holidayRepository.save(holiday);
        
        // Update cache
        String cacheKey = saved.getMarket() + ":" + saved.getYear();
        Set<LocalDate> holidays = holidayCache.getOrDefault(cacheKey, new HashSet<>());
        holidays.add(saved.getHolidayDate());
        holidayCache.put(cacheKey, holidays);
        
        log.info("Added holiday: {} - {} on {}", saved.getMarket(), 
                saved.getHolidayName(), saved.getHolidayDate());
        
        return saved;
    }

    /**
     * Removes a holiday.
     * 
     * @param holidayId The holiday ID
     */
    public void removeHoliday(Long holidayId) {
        holidayRepository.findById(holidayId).ifPresent(holiday -> {
            holidayRepository.delete(holiday);
            
            // Update cache
            String cacheKey = holiday.getMarket() + ":" + holiday.getYear();
            Set<LocalDate> holidays = holidayCache.get(cacheKey);
            if (holidays != null) {
                holidays.remove(holiday.getHolidayDate());
                holidayCache.put(cacheKey, holidays);
            }
            
            log.info("Removed holiday: {} - {} on {}", holiday.getMarket(), 
                    holiday.getHolidayName(), holiday.getHolidayDate());
        });
    }

    /**
     * Bulk imports holidays from external source.
     * 
     * @param holidays List of holidays to import
     * @return Number of holidays imported
     */
    public int bulkImportHolidays(List<Holiday> holidays) {
        log.info("Bulk importing {} holidays", holidays.size());
        
        LocalDateTime now = LocalDateTime.now();
        holidays.forEach(h -> {
            h.setCreatedAt(now);
            h.setUpdatedAt(now);
        });
        
        List<Holiday> saved = holidayRepository.saveAll(holidays);
        
        // Refresh cache
        loadHolidaysIntoCache();
        
        log.info("Successfully imported {} holidays", saved.size());
        return saved.size();
    }

    /**
     * Refreshes the holiday cache from database.
     * Scheduled to run daily at midnight.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void refreshCache() {
        log.info("Refreshing holiday cache");
        loadHolidaysIntoCache();
    }

    /**
     * Gets supported market codes.
     * 
     * @return List of market codes
     */
    public List<String> getSupportedMarkets() {
        return Arrays.stream(MarketCode.values())
            .map(Enum::name)
            .collect(Collectors.toList());
    }

    /**
     * Checks if the current time is within trading hours for a market.
     * This is a simplified implementation - actual trading hours vary by market.
     * 
     * @param market The market code
     * @param zoneId The timezone to check
     * @return true if within trading hours
     */
    public boolean isWithinTradingHours(String market, ZoneId zoneId) {
        LocalDateTime now = LocalDateTime.now(zoneId);
        LocalDate today = now.toLocalDate();
        
        // First check if today is a trading day
        if (!isTradingDay(market, today)) {
            return false;
        }
        
        // Simplified trading hours (would be configuration-driven in production)
        int hour = now.getHour();
        
        return switch (market) {
            case "US" -> hour >= 9 && hour < 16;  // 9:30 AM - 4:00 PM EST
            case "UK" -> hour >= 8 && hour < 16;  // 8:00 AM - 4:30 PM GMT
            case "JP" -> hour >= 9 && hour < 15;  // 9:00 AM - 3:00 PM JST
            case "HK" -> hour >= 9 && hour < 16;  // 9:30 AM - 4:00 PM HKT
            default -> hour >= 9 && hour < 17;    // Default 9 AM - 5 PM
        };
    }

    /**
     * Gets statistics about holidays in the system.
     * 
     * @return Statistics as a formatted string
     */
    public String getStatistics() {
        long totalHolidays = holidayRepository.count();
        int cachedYears = holidayCache.size();
        
        StringBuilder stats = new StringBuilder();
        stats.append("Holiday Calendar Statistics:\n");
        stats.append(String.format("  Total holidays in database: %d\n", totalHolidays));
        stats.append(String.format("  Cached market-years: %d\n", cachedYears));
        stats.append(String.format("  Supported markets: %d\n", marketWeekends.size()));
        
        return stats.toString();
    }
}