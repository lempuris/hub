package com.refinitiv.markethub.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a holiday in the Market Data Hub holiday calendar.
 * 
 * Holidays are essential for:
 * - Date calculations and interpolation
 * - Determining valid trading days
 * - Providing holiday information to RA without GEM dependency
 * 
 * Different markets and exchanges may have different holiday schedules,
 * so holidays are associated with specific markets.
 * 
 * @author Market Data Hub Team
 * @version 1.0
 */
@Entity
@Table(name = "holidays", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"holiday_date", "market", "exchange"}),
       indexes = {
           @Index(name = "idx_holiday_date", columnList = "holiday_date"),
           @Index(name = "idx_holiday_market", columnList = "market"),
           @Index(name = "idx_holiday_exchange", columnList = "exchange")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Holiday {

    /**
     * Unique identifier for the holiday record.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Date of the holiday.
     * This is the actual date when markets are closed.
     */
    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    /**
     * Name of the holiday.
     * Example: "Christmas Day", "New Year's Day", "Independence Day"
     */
    @Column(nullable = false, length = 255)
    private String holidayName;

    /**
     * Market or region this holiday applies to.
     * Example: "US", "UK", "JP", "EU"
     */
    @Column(nullable = false, length = 50)
    private String market;

    /**
     * Specific exchange this holiday applies to.
     * Example: "NYSE", "NASDAQ", "LSE", "TSE"
     * Can be "ALL" for market-wide holidays.
     */
    @Column(length = 50)
    private String exchange;

    /**
     * Type of holiday.
     * Helps categorize holidays for different handling.
     */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private HolidayType holidayType;

    /**
     * Indicates if this is a full-day closure.
     * Some holidays may have early closures instead.
     */
    @Column(nullable = false)
    private Boolean isFullDay;

    /**
     * For partial holidays, the early close time.
     * Format: "HH:MM" in market local time.
     * Example: "13:00" for 1 PM close
     */
    @Column(length = 10)
    private String earlyCloseTime;

    /**
     * Additional notes or description about the holiday.
     * Can include special instructions or exceptions.
     */
    @Column(columnDefinition = "TEXT")
    private String notes;

    /**
     * Year this holiday applies to.
     * Useful for querying holidays by year.
     */
    @Column(name = "holiday_year", nullable = false)
    private Integer year;

    /**
     * Indicates if this holiday is currently active.
     * Can be used to disable holidays without deleting them.
     */
    @Column(nullable = false)
    private Boolean active;

    /**
     * Timestamp when the holiday was created.
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when the holiday was last updated.
     */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * User who last updated the holiday.
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
        if (isFullDay == null) {
            isFullDay = true;
        }
        if (year == null && holidayDate != null) {
            year = holidayDate.getYear();
        }
    }

    /**
     * Pre-update method to update timestamp before updates.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (year == null && holidayDate != null) {
            year = holidayDate.getYear();
        }
    }

    /**
     * Checks if this holiday is applicable to a given exchange.
     * 
     * @param exchangeCode The exchange code to check
     * @return true if the holiday applies to the exchange
     */
    public boolean appliesTo(String exchangeCode) {
        if (exchange == null || "ALL".equalsIgnoreCase(exchange)) {
            return true;
        }
        return exchange.equalsIgnoreCase(exchangeCode);
    }

    /**
     * Enum defining types of holidays.
     */
    public enum HolidayType {
        PUBLIC,          // Public/National holiday
        BANK,            // Bank holiday
        EXCHANGE,        // Exchange-specific holiday
        SETTLEMENT,      // Settlement holiday (no settlements but trading continues)
        SPECIAL,         // Special one-time closures
        WEEKEND          // Weekend days (if needed for certain markets)
    }
}