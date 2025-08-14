package com.refinitiv.markethub.service;

import com.refinitiv.markethub.model.MarketData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for interpolating missing market data points.
 * 
 * This service provides:
 * - Linear interpolation between known data points
 * - Time-weighted interpolation for irregular data
 * - Curve fitting for yield curves and volatility surfaces
 * - Gap filling for missing data points
 * - Extrapolation for forward-looking estimates
 * 
 * Interpolation is essential for:
 * - Creating continuous price curves
 * - Estimating prices for illiquid instruments
 * - Smoothing volatile market data
 * - Generating synthetic data points for analysis
 * 
 * Assigned to: Dustin (as per requirements)
 * 
 * @author Market Data Hub Team - Dustin
 * @version 1.0
 */
@Service
@Slf4j
public class InterpolationService {

    /**
     * Interpolation method configuration.
     */
    @Value("${markethub.interpolation.enabled:true}")
    private boolean interpolationEnabled;

    @Value("${markethub.interpolation.method:LINEAR}")
    private String defaultMethod;

    @Value("${markethub.interpolation.max-gap-minutes:5}")
    private int maxGapMinutes;

    /**
     * Cache of recent data points for interpolation.
     * Key: Symbol, Value: Time-ordered list of market data
     */
    private final Map<String, TreeMap<Long, MarketData>> dataHistory = new ConcurrentHashMap<>();

    /**
     * Supported interpolation methods.
     */
    public enum InterpolationMethod {
        LINEAR("Linear interpolation between two points"),
        CUBIC("Cubic spline interpolation for smooth curves"),
        POLYNOMIAL("Polynomial interpolation for complex curves"),
        TIME_WEIGHTED("Time-weighted average interpolation"),
        LAST_VALUE("Carry forward last known value"),
        NONE("No interpolation");

        private final String description;

        InterpolationMethod(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Data point for interpolation calculations.
     */
    private static class DataPoint {
        final long timestamp;
        final BigDecimal value;

        DataPoint(long timestamp, BigDecimal value) {
            this.timestamp = timestamp;
            this.value = value;
        }
    }

    /**
     * Stores market data for interpolation purposes.
     * 
     * @param marketData The market data to store
     */
    public void addDataPoint(MarketData marketData) {
        if (marketData == null || marketData.getSymbol() == null) {
            return;
        }

        String symbol = marketData.getSymbol();
        TreeMap<Long, MarketData> history = dataHistory.computeIfAbsent(symbol, k -> new TreeMap<>());
        
        // Store the data point
        history.put(marketData.getTimestamp(), marketData);
        
        // Keep only recent data (last hour)
        long cutoffTime = System.currentTimeMillis() - (60 * 60 * 1000);
        history.entrySet().removeIf(entry -> entry.getKey() < cutoffTime);
        
        log.trace("Added data point for {} at timestamp {}", symbol, marketData.getTimestamp());
    }

    /**
     * Interpolates missing market data for a specific timestamp.
     * 
     * @param symbol The symbol to interpolate
     * @param timestamp The timestamp to interpolate for
     * @return Interpolated market data or null if interpolation not possible
     */
    public MarketData interpolate(String symbol, long timestamp) {
        if (!interpolationEnabled) {
            log.debug("Interpolation is disabled");
            return null;
        }

        TreeMap<Long, MarketData> history = dataHistory.get(symbol);
        if (history == null || history.isEmpty()) {
            log.debug("No historical data available for {}", symbol);
            return null;
        }

        // Check if we have exact data
        if (history.containsKey(timestamp)) {
            return history.get(timestamp);
        }

        // Get surrounding data points
        Map.Entry<Long, MarketData> lowerEntry = history.floorEntry(timestamp);
        Map.Entry<Long, MarketData> higherEntry = history.ceilingEntry(timestamp);

        // Determine interpolation method
        InterpolationMethod method = InterpolationMethod.valueOf(defaultMethod);
        
        return performInterpolation(symbol, timestamp, lowerEntry, higherEntry, method);
    }

    /**
     * Performs the actual interpolation based on the selected method.
     */
    private MarketData performInterpolation(String symbol, long timestamp,
                                           Map.Entry<Long, MarketData> lowerEntry,
                                           Map.Entry<Long, MarketData> higherEntry,
                                           InterpolationMethod method) {
        
        // Check if gap is too large
        if (lowerEntry != null && higherEntry != null) {
            long gapMinutes = (higherEntry.getKey() - lowerEntry.getKey()) / (60 * 1000);
            if (gapMinutes > maxGapMinutes) {
                log.debug("Gap too large for interpolation: {} minutes", gapMinutes);
                return null;
            }
        }

        switch (method) {
            case LINEAR:
                return linearInterpolation(symbol, timestamp, lowerEntry, higherEntry);
            
            case TIME_WEIGHTED:
                return timeWeightedInterpolation(symbol, timestamp, lowerEntry, higherEntry);
            
            case LAST_VALUE:
                return lastValueInterpolation(symbol, timestamp, lowerEntry);
            
            case CUBIC:
                return cubicInterpolation(symbol, timestamp, lowerEntry, higherEntry);
            
            case POLYNOMIAL:
                // For simplicity, falling back to linear for now
                // Full polynomial implementation would require more data points
                return linearInterpolation(symbol, timestamp, lowerEntry, higherEntry);
            
            case NONE:
            default:
                return null;
        }
    }

    /**
     * Performs linear interpolation between two data points.
     */
    private MarketData linearInterpolation(String symbol, long timestamp,
                                          Map.Entry<Long, MarketData> lowerEntry,
                                          Map.Entry<Long, MarketData> higherEntry) {
        
        if (lowerEntry == null || higherEntry == null) {
            // Can't interpolate without both points
            return lastValueInterpolation(symbol, timestamp, lowerEntry);
        }

        MarketData lower = lowerEntry.getValue();
        MarketData higher = higherEntry.getValue();
        
        // Calculate interpolation factor (0 to 1)
        BigDecimal factor = BigDecimal.valueOf(timestamp - lowerEntry.getKey())
            .divide(BigDecimal.valueOf(higherEntry.getKey() - lowerEntry.getKey()), 8, RoundingMode.HALF_UP);
        
        // Create interpolated market data
        MarketData interpolated = new MarketData();
        interpolated.setSymbol(symbol);
        interpolated.setTimestamp(timestamp);
        interpolated.setSource("INTERPOLATED");
        
        // Interpolate prices
        interpolated.setBidPrice(interpolateValue(lower.getBidPrice(), higher.getBidPrice(), factor));
        interpolated.setAskPrice(interpolateValue(lower.getAskPrice(), higher.getAskPrice(), factor));
        interpolated.setLastPrice(interpolateValue(lower.getLastPrice(), higher.getLastPrice(), factor));
        interpolated.setOpenPrice(lower.getOpenPrice()); // Keep open price from lower
        
        // Calculate high/low
        if (lower.getHighPrice() != null && higher.getHighPrice() != null) {
            interpolated.setHighPrice(lower.getHighPrice().max(higher.getHighPrice()));
        }
        if (lower.getLowPrice() != null && higher.getLowPrice() != null) {
            interpolated.setLowPrice(lower.getLowPrice().min(higher.getLowPrice()));
        }
        
        interpolated.setPreviousClose(lower.getPreviousClose());
        
        // Interpolate sizes (rounded to nearest integer)
        interpolated.setBidSize(interpolateLong(lower.getBidSize(), higher.getBidSize(), factor));
        interpolated.setAskSize(interpolateLong(lower.getAskSize(), higher.getAskSize(), factor));
        interpolated.setVolume(interpolateLong(lower.getVolume(), higher.getVolume(), factor));
        
        log.debug("Linear interpolation for {} at {} (factor: {})", symbol, timestamp, factor);
        
        return interpolated;
    }

    /**
     * Performs time-weighted interpolation.
     */
    private MarketData timeWeightedInterpolation(String symbol, long timestamp,
                                                Map.Entry<Long, MarketData> lowerEntry,
                                                Map.Entry<Long, MarketData> higherEntry) {
        
        if (lowerEntry == null || higherEntry == null) {
            return lastValueInterpolation(symbol, timestamp, lowerEntry);
        }

        // Calculate time weights
        long timeDiff1 = timestamp - lowerEntry.getKey();
        long timeDiff2 = higherEntry.getKey() - timestamp;
        long totalDiff = higherEntry.getKey() - lowerEntry.getKey();
        
        // Weights are inversely proportional to time distance
        BigDecimal weight1 = BigDecimal.ONE.subtract(
            BigDecimal.valueOf(timeDiff1).divide(BigDecimal.valueOf(totalDiff), 8, RoundingMode.HALF_UP));
        BigDecimal weight2 = BigDecimal.ONE.subtract(
            BigDecimal.valueOf(timeDiff2).divide(BigDecimal.valueOf(totalDiff), 8, RoundingMode.HALF_UP));
        
        // Normalize weights
        BigDecimal totalWeight = weight1.add(weight2);
        weight1 = weight1.divide(totalWeight, 8, RoundingMode.HALF_UP);
        weight2 = weight2.divide(totalWeight, 8, RoundingMode.HALF_UP);
        
        MarketData lower = lowerEntry.getValue();
        MarketData higher = higherEntry.getValue();
        
        // Create weighted average
        MarketData interpolated = new MarketData();
        interpolated.setSymbol(symbol);
        interpolated.setTimestamp(timestamp);
        interpolated.setSource("TIME_WEIGHTED");
        
        // Calculate weighted averages
        interpolated.setBidPrice(weightedAverage(lower.getBidPrice(), higher.getBidPrice(), weight1, weight2));
        interpolated.setAskPrice(weightedAverage(lower.getAskPrice(), higher.getAskPrice(), weight1, weight2));
        interpolated.setLastPrice(weightedAverage(lower.getLastPrice(), higher.getLastPrice(), weight1, weight2));
        
        // Copy other fields from closer data point
        MarketData closer = weight1.compareTo(weight2) > 0 ? lower : higher;
        interpolated.setOpenPrice(closer.getOpenPrice());
        interpolated.setHighPrice(closer.getHighPrice());
        interpolated.setLowPrice(closer.getLowPrice());
        interpolated.setPreviousClose(closer.getPreviousClose());
        interpolated.setBidSize(closer.getBidSize());
        interpolated.setAskSize(closer.getAskSize());
        interpolated.setVolume(closer.getVolume());
        
        log.debug("Time-weighted interpolation for {} at {} (weights: {}, {})", 
                 symbol, timestamp, weight1, weight2);
        
        return interpolated;
    }

    /**
     * Performs cubic spline interpolation.
     * Simplified version using Hermite interpolation.
     */
    private MarketData cubicInterpolation(String symbol, long timestamp,
                                         Map.Entry<Long, MarketData> lowerEntry,
                                         Map.Entry<Long, MarketData> higherEntry) {
        
        if (lowerEntry == null || higherEntry == null) {
            return lastValueInterpolation(symbol, timestamp, lowerEntry);
        }

        // For a proper cubic spline, we'd need at least 4 points
        // Using simplified Hermite interpolation with estimated derivatives
        TreeMap<Long, MarketData> history = dataHistory.get(symbol);
        
        // Get additional points for derivative estimation
        Map.Entry<Long, MarketData> prevLower = history.lowerEntry(lowerEntry.getKey());
        Map.Entry<Long, MarketData> nextHigher = history.higherEntry(higherEntry.getKey());
        
        // Estimate derivatives (slopes) at the two points
        BigDecimal slope1 = estimateSlope(prevLower, lowerEntry, higherEntry);
        BigDecimal slope2 = estimateSlope(lowerEntry, higherEntry, nextHigher);
        
        // Normalized time parameter (0 to 1)
        BigDecimal t = BigDecimal.valueOf(timestamp - lowerEntry.getKey())
            .divide(BigDecimal.valueOf(higherEntry.getKey() - lowerEntry.getKey()), 8, RoundingMode.HALF_UP);
        
        // Hermite basis functions
        BigDecimal t2 = t.multiply(t);
        BigDecimal t3 = t2.multiply(t);
        
        BigDecimal h00 = BigDecimal.valueOf(2).multiply(t3)
            .subtract(BigDecimal.valueOf(3).multiply(t2))
            .add(BigDecimal.ONE);
        BigDecimal h10 = t3.subtract(BigDecimal.valueOf(2).multiply(t2)).add(t);
        BigDecimal h01 = BigDecimal.valueOf(-2).multiply(t3)
            .add(BigDecimal.valueOf(3).multiply(t2));
        BigDecimal h11 = t3.subtract(t2);
        
        MarketData lower = lowerEntry.getValue();
        MarketData higher = higherEntry.getValue();
        
        // Create interpolated data
        MarketData interpolated = new MarketData();
        interpolated.setSymbol(symbol);
        interpolated.setTimestamp(timestamp);
        interpolated.setSource("CUBIC");
        
        // Apply Hermite interpolation to last price
        if (lower.getLastPrice() != null && higher.getLastPrice() != null) {
            BigDecimal p0 = lower.getLastPrice();
            BigDecimal p1 = higher.getLastPrice();
            BigDecimal deltaT = BigDecimal.valueOf(higherEntry.getKey() - lowerEntry.getKey()).divide(BigDecimal.valueOf(1000), 8, RoundingMode.HALF_UP);
            BigDecimal m0 = slope1.multiply(deltaT);
            BigDecimal m1 = slope2.multiply(deltaT);
            
            BigDecimal interpolatedPrice = h00.multiply(p0)
                .add(h10.multiply(m0))
                .add(h01.multiply(p1))
                .add(h11.multiply(m1));
            
            interpolated.setLastPrice(interpolatedPrice);
            
            // Apply same interpolation to bid/ask with spread preservation
            BigDecimal spread = BigDecimal.ZERO;
            if (lower.getBidPrice() != null && lower.getAskPrice() != null) {
                spread = lower.getAskPrice().subtract(lower.getBidPrice());
            }
            
            interpolated.setBidPrice(interpolatedPrice.subtract(spread.divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP)));
            interpolated.setAskPrice(interpolatedPrice.add(spread.divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP)));
        }
        
        // Copy other fields from linear interpolation
        MarketData linear = linearInterpolation(symbol, timestamp, lowerEntry, higherEntry);
        if (linear != null) {
            interpolated.setOpenPrice(linear.getOpenPrice());
            interpolated.setHighPrice(linear.getHighPrice());
            interpolated.setLowPrice(linear.getLowPrice());
            interpolated.setPreviousClose(linear.getPreviousClose());
            interpolated.setBidSize(linear.getBidSize());
            interpolated.setAskSize(linear.getAskSize());
            interpolated.setVolume(linear.getVolume());
        }
        
        log.debug("Cubic interpolation for {} at {} (t={})", symbol, timestamp, t);
        
        return interpolated;
    }

    /**
     * Uses last known value (no interpolation).
     */
    private MarketData lastValueInterpolation(String symbol, long timestamp,
                                             Map.Entry<Long, MarketData> lowerEntry) {
        
        if (lowerEntry == null) {
            log.debug("No previous data point for last value interpolation");
            return null;
        }

        // Clone the last known value with updated timestamp
        MarketData lastKnown = lowerEntry.getValue();
        MarketData carried = new MarketData();
        
        carried.setSymbol(symbol);
        carried.setTimestamp(timestamp);
        carried.setSource("LAST_VALUE");
        carried.setBidPrice(lastKnown.getBidPrice());
        carried.setAskPrice(lastKnown.getAskPrice());
        carried.setLastPrice(lastKnown.getLastPrice());
        carried.setOpenPrice(lastKnown.getOpenPrice());
        carried.setHighPrice(lastKnown.getHighPrice());
        carried.setLowPrice(lastKnown.getLowPrice());
        carried.setPreviousClose(lastKnown.getPreviousClose());
        carried.setBidSize(lastKnown.getBidSize());
        carried.setAskSize(lastKnown.getAskSize());
        carried.setVolume(lastKnown.getVolume());
        
        log.debug("Last value interpolation for {} at {} (from {})", 
                 symbol, timestamp, lowerEntry.getKey());
        
        return carried;
    }

    /**
     * Fills gaps in a time series of market data.
     * 
     * @param symbol The symbol to fill gaps for
     * @param startTime Start of the time range
     * @param endTime End of the time range
     * @param intervalMillis Desired interval between data points
     * @return List of market data including interpolated points
     */
    public List<MarketData> fillGaps(String symbol, long startTime, long endTime, long intervalMillis) {
        List<MarketData> filledData = new ArrayList<>();
        
        TreeMap<Long, MarketData> history = dataHistory.get(symbol);
        if (history == null || history.isEmpty()) {
            return filledData;
        }

        // Get all actual data points in range
        NavigableMap<Long, MarketData> rangeData = history.subMap(startTime, true, endTime, true);
        
        // Generate regular time points
        for (long timestamp = startTime; timestamp <= endTime; timestamp += intervalMillis) {
            MarketData data = rangeData.get(timestamp);
            
            if (data == null) {
                // Need to interpolate
                data = interpolate(symbol, timestamp);
            }
            
            if (data != null) {
                filledData.add(data);
            }
        }
        
        log.debug("Filled {} gaps for {} between {} and {}", 
                 filledData.size() - rangeData.size(), symbol, startTime, endTime);
        
        return filledData;
    }

    /**
     * Extrapolates future values based on historical trend.
     * 
     * @param symbol The symbol to extrapolate
     * @param futureTimestamp The future timestamp to extrapolate to
     * @param lookbackMinutes How many minutes of history to use for trend
     * @return Extrapolated market data or null if not enough history
     */
    public MarketData extrapolate(String symbol, long futureTimestamp, int lookbackMinutes) {
        TreeMap<Long, MarketData> history = dataHistory.get(symbol);
        if (history == null || history.size() < 2) {
            return null;
        }

        long lookbackTime = System.currentTimeMillis() - (lookbackMinutes * 60 * 1000);
        NavigableMap<Long, MarketData> recentData = history.tailMap(lookbackTime, true);
        
        if (recentData.size() < 2) {
            return null;
        }

        // Calculate linear trend
        List<DataPoint> points = recentData.entrySet().stream()
            .filter(e -> e.getValue().getLastPrice() != null)
            .map(e -> new DataPoint(e.getKey(), e.getValue().getLastPrice()))
            .collect(Collectors.toList());
        
        if (points.size() < 2) {
            return null;
        }

        // Simple linear regression
        BigDecimal[] trend = calculateLinearTrend(points);
        BigDecimal slope = trend[0];
        BigDecimal intercept = trend[1];
        
        // Extrapolate price
        BigDecimal extrapolatedPrice = slope.multiply(BigDecimal.valueOf(futureTimestamp)).add(intercept);
        
        // Get most recent data for other fields
        MarketData latest = history.lastEntry().getValue();
        
        // Create extrapolated data
        MarketData extrapolated = new MarketData();
        extrapolated.setSymbol(symbol);
        extrapolated.setTimestamp(futureTimestamp);
        extrapolated.setSource("EXTRAPOLATED");
        extrapolated.setLastPrice(extrapolatedPrice);
        
        // Maintain spread from latest data
        if (latest.getBidPrice() != null && latest.getAskPrice() != null) {
            BigDecimal spread = latest.getAskPrice().subtract(latest.getBidPrice());
            extrapolated.setBidPrice(extrapolatedPrice.subtract(spread.divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP)));
            extrapolated.setAskPrice(extrapolatedPrice.add(spread.divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP)));
        }
        
        // Copy other fields from latest
        extrapolated.setOpenPrice(latest.getOpenPrice());
        extrapolated.setHighPrice(extrapolatedPrice.max(latest.getHighPrice() != null ? latest.getHighPrice() : extrapolatedPrice));
        extrapolated.setLowPrice(extrapolatedPrice.min(latest.getLowPrice() != null ? latest.getLowPrice() : extrapolatedPrice));
        extrapolated.setPreviousClose(latest.getPreviousClose());
        extrapolated.setBidSize(latest.getBidSize());
        extrapolated.setAskSize(latest.getAskSize());
        extrapolated.setVolume(latest.getVolume());
        
        log.debug("Extrapolated {} to {} with price {} (slope: {})", 
                 symbol, futureTimestamp, extrapolatedPrice, slope);
        
        return extrapolated;
    }

    // ==================== Utility Methods ====================

    /**
     * Interpolates a BigDecimal value.
     */
    private BigDecimal interpolateValue(BigDecimal value1, BigDecimal value2, BigDecimal factor) {
        if (value1 == null || value2 == null) {
            return value1 != null ? value1 : value2;
        }
        // value1 + (value2 - value1) * factor
        return value1.add(value2.subtract(value1).multiply(factor));
    }

    /**
     * Interpolates a long value.
     */
    private Long interpolateLong(Long value1, Long value2, BigDecimal factor) {
        if (value1 == null || value2 == null) {
            return value1 != null ? value1 : value2;
        }
        BigDecimal result = BigDecimal.valueOf(value1)
            .add(BigDecimal.valueOf(value2 - value1).multiply(factor));
        return result.longValue();
    }

    /**
     * Calculates weighted average.
     */
    private BigDecimal weightedAverage(BigDecimal value1, BigDecimal value2, BigDecimal weight1, BigDecimal weight2) {
        if (value1 == null || value2 == null) {
            return value1 != null ? value1 : value2;
        }
        return value1.multiply(weight1).add(value2.multiply(weight2));
    }

    /**
     * Estimates slope for cubic interpolation.
     */
    private BigDecimal estimateSlope(Map.Entry<Long, MarketData> prev,
                                    Map.Entry<Long, MarketData> current,
                                    Map.Entry<Long, MarketData> next) {
        
        if (current == null || current.getValue().getLastPrice() == null) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal slope = BigDecimal.ZERO;
        int count = 0;
        
        // Calculate slope from previous point
        if (prev != null && prev.getValue().getLastPrice() != null) {
            BigDecimal timeDiff = BigDecimal.valueOf(current.getKey() - prev.getKey()).divide(BigDecimal.valueOf(1000), 8, RoundingMode.HALF_UP);
            BigDecimal priceDiff = current.getValue().getLastPrice().subtract(prev.getValue().getLastPrice());
            slope = slope.add(priceDiff.divide(timeDiff, 8, RoundingMode.HALF_UP));
            count++;
        }
        
        // Calculate slope to next point
        if (next != null && next.getValue().getLastPrice() != null) {
            BigDecimal timeDiff = BigDecimal.valueOf(next.getKey() - current.getKey()).divide(BigDecimal.valueOf(1000), 8, RoundingMode.HALF_UP);
            BigDecimal priceDiff = next.getValue().getLastPrice().subtract(current.getValue().getLastPrice());
            slope = slope.add(priceDiff.divide(timeDiff, 8, RoundingMode.HALF_UP));
            count++;
        }
        
        return count > 0 ? slope.divide(BigDecimal.valueOf(count), 8, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    /**
     * Calculates linear trend using least squares.
     * 
     * @param points Data points for trend calculation
     * @return Array with [slope, intercept]
     */
    private BigDecimal[] calculateLinearTrend(List<DataPoint> points) {
        int n = points.size();
        BigDecimal sumX = BigDecimal.ZERO;
        BigDecimal sumY = BigDecimal.ZERO;
        BigDecimal sumXY = BigDecimal.ZERO;
        BigDecimal sumX2 = BigDecimal.ZERO;
        
        for (DataPoint point : points) {
            BigDecimal x = BigDecimal.valueOf(point.timestamp);
            BigDecimal y = point.value;
            
            sumX = sumX.add(x);
            sumY = sumY.add(y);
            sumXY = sumXY.add(x.multiply(y));
            sumX2 = sumX2.add(x.multiply(x));
        }
        
        BigDecimal nBig = BigDecimal.valueOf(n);
        BigDecimal denominator = nBig.multiply(sumX2).subtract(sumX.multiply(sumX));
        
        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            return new BigDecimal[]{BigDecimal.ZERO, sumY.divide(nBig, 8, RoundingMode.HALF_UP)};
        }
        
        BigDecimal slope = nBig.multiply(sumXY).subtract(sumX.multiply(sumY))
            .divide(denominator, 8, RoundingMode.HALF_UP);
        BigDecimal intercept = sumY.subtract(slope.multiply(sumX))
            .divide(nBig, 8, RoundingMode.HALF_UP);
        
        return new BigDecimal[]{slope, intercept};
    }

    /**
     * Clears history for a specific symbol.
     * 
     * @param symbol The symbol to clear history for
     */
    public void clearHistory(String symbol) {
        dataHistory.remove(symbol);
        log.debug("Cleared interpolation history for {}", symbol);
    }

    /**
     * Clears all interpolation history.
     */
    public void clearAllHistory() {
        dataHistory.clear();
        log.info("Cleared all interpolation history");
    }

    /**
     * Gets statistics about interpolation service.
     * 
     * @return Statistics as formatted string
     */
    public String getStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("Interpolation Service Statistics:\n");
        stats.append(String.format("  Enabled: %s\n", interpolationEnabled));
        stats.append(String.format("  Default Method: %s\n", defaultMethod));
        stats.append(String.format("  Max Gap Minutes: %d\n", maxGapMinutes));
        stats.append(String.format("  Symbols Tracked: %d\n", dataHistory.size()));
        
        int totalPoints = dataHistory.values().stream()
            .mapToInt(TreeMap::size)
            .sum();
        stats.append(String.format("  Total Data Points: %d\n", totalPoints));
        
        return stats.toString();
    }

    /**
     * Checks if interpolation is enabled.
     * 
     * @return true if enabled
     */
    public boolean isEnabled() {
        return interpolationEnabled;
    }

    /**
     * Sets interpolation enabled state.
     * 
     * @param enabled The enabled state
     */
    public void setEnabled(boolean enabled) {
        this.interpolationEnabled = enabled;
        log.info("Interpolation service {}", enabled ? "enabled" : "disabled");
    }
}