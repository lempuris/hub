# Market Data Hub V1 - Testing Guide

## Quick Start Testing (No External Dependencies)

### 1. Run with H2 In-Memory Database

This is the easiest way to test without setting up PostgreSQL:

```bash
# Windows
gradlew.bat bootRun --args='--spring.profiles.active=test'

# Linux/Mac
./gradlew bootRun --args='--spring.profiles.active=test'
```

The application will start with:
- H2 in-memory database (no PostgreSQL needed)
- Web server on http://localhost:8080/api
- FIX acceptor on port 9876
- H2 Console at http://localhost:8080/api/h2-console

### 2. Run Automated Tests

```bash
# Windows
test-hub.bat

# Linux/Mac
./test-hub.sh
```

## Manual Testing Steps

### Step 1: Verify Health

```bash
curl http://localhost:8080/api/hub/health
```

Expected response:
```json
{
  "status": "UP",
  "components": {
    "fix": "UP",
    "hazelcast": "UP",
    "hazelcast_cluster_size": "1"
  },
  "statistics": {
    "active_market_makers": 0,
    "connected_market_makers": 0,
    "cached_symbols": 0
  }
}
```

### Step 2: Create Market Makers

Create Risk Analytics market maker:
```bash
curl -X POST http://localhost:8080/api/hub/marketmakers \
  -H "Content-Type: application/json" \
  -d '{
    "marketMakerId": "RA",
    "name": "Risk Analytics",
    "compId": "RA",
    "type": "RISK_ANALYTICS",
    "connectionMode": "INITIATOR",
    "port": 9876,
    "subscribedSymbols": ["AAPL", "GOOGL", "SPX*"],
    "active": true
  }'
```

Create CMF market maker:
```bash
curl -X POST http://localhost:8080/api/hub/marketmakers \
  -H "Content-Type: application/json" \
  -d '{
    "marketMakerId": "CMF",
    "name": "Capital Markets Framework",
    "compId": "CMF",
    "type": "CAPITAL_MARKETS",
    "connectionMode": "INITIATOR",
    "port": 9876,
    "subscribedSymbols": ["*"],
    "active": true
  }'
```

### Step 3: Generate Test Market Data

Generate test data for multiple symbols:
```bash
# Generate AAPL data
curl -X POST http://localhost:8080/api/hub/marketdata/test/AAPL

# Generate GOOGL data
curl -X POST http://localhost:8080/api/hub/marketdata/test/GOOGL

# Generate MSFT data
curl -X POST http://localhost:8080/api/hub/marketdata/test/MSFT
```

### Step 4: Retrieve Market Data

Get all cached data:
```bash
curl http://localhost:8080/api/hub/marketdata
```

Get specific symbol:
```bash
curl http://localhost:8080/api/hub/marketdata/AAPL
```

### Step 5: Check Status

```bash
curl http://localhost:8080/api/hub/status
```

## Testing with PostgreSQL

### Option 1: Docker Compose

```bash
# Start PostgreSQL
docker-compose up -d

# Run application with default profile
gradlew.bat bootRun
```

### Option 2: Local PostgreSQL

1. Create database:
```sql
CREATE DATABASE markethub;
CREATE USER markethub WITH PASSWORD 'markethub';
GRANT ALL PRIVILEGES ON DATABASE markethub TO markethub;
```

2. Run application:
```bash
gradlew.bat bootRun
```

## FIX Protocol Testing

### Using QuickFIX Client

1. Configure your FIX client with:
```
BeginString=FIX.4.4
SenderCompID=TEST_CLIENT
TargetCompID=MARKETHUB
SocketConnectHost=localhost
SocketConnectPort=9876
HeartBtInt=30
```

2. Send Market Data Request (35=V):
```
8=FIX.4.4|9=xxx|35=V|49=TEST_CLIENT|56=MARKETHUB|34=1|52=xxx|
262=REQ001|263=1|264=0|265=0|146=1|55=AAPL|10=xxx|
```

### Testing FIX Connection

```bash
# Send test message to connected session
curl -X POST http://localhost:8080/api/hub/fix/test/RA
```

## Monitoring Endpoints

### Actuator Endpoints

- Health: http://localhost:8080/api/actuator/health
- Metrics: http://localhost:8080/api/actuator/metrics
- Prometheus: http://localhost:8080/api/actuator/prometheus

### Custom Endpoints

- Hub Health: http://localhost:8080/api/hub/health
- Hub Status: http://localhost:8080/api/hub/status
- Market Makers: http://localhost:8080/api/hub/marketmakers
- Market Data: http://localhost:8080/api/hub/marketdata

## Troubleshooting

### Application Won't Start

1. Check port 8080 is free:
```bash
netstat -an | findstr 8080
```

2. Check port 9876 (FIX) is free:
```bash
netstat -an | findstr 9876
```

3. Check logs:
```bash
type logs\markethub.log
```

### Database Connection Issues

1. For H2 testing, use test profile:
```bash
gradlew.bat bootRun --args='--spring.profiles.active=test'
```

2. For PostgreSQL, verify connection:
```bash
psql -h localhost -U markethub -d markethub
```

### FIX Connection Issues

1. Check FIX logs:
```bash
dir logs\fix\
```

2. Verify acceptor is running:
```bash
curl http://localhost:8080/api/hub/status
```

## Performance Testing

### Generate Load

```bash
# PowerShell script for load testing
for ($i=1; $i -le 100; $i++) {
    curl -X POST "http://localhost:8080/api/hub/marketdata/test/TEST$i"
}
```

### Monitor Performance

1. Check Prometheus metrics:
```bash
curl http://localhost:8080/api/actuator/prometheus | findstr markethub
```

2. Check service statistics:
```bash
curl http://localhost:8080/api/hub/status
```

## Expected Test Results

### Successful Health Check
- Status: UP
- FIX: UP
- Hazelcast: UP
- Cluster Size: >= 1

### Successful Market Data Generation
- Symbol populated
- Bid/Ask prices present
- Spread reasonable (< 10%)
- Quality: LIVE
- Source: TEST

### Successful Market Maker Creation
- ID assigned
- Active: true
- Connection Status: DISCONNECTED (initially)
- Subscriptions saved

## Testing New Services

### Refinitiv ETA Service

The Refinitiv ETA service connects to Refinitiv Real-Time for market data:

1. **Enable the service** (disabled by default):
```properties
# application.properties
refinitiv.eta.enabled=true
refinitiv.eta.host=elektron.refinitiv.com
refinitiv.eta.port=14002
refinitiv.eta.username=YOUR_USERNAME
```

2. **Test in simulation mode** (no credentials needed):
- Service includes simulation mode for testing
- Generates realistic market data
- Check logs: `grep "Refinitiv ETA" logs/markethub.log`

3. **Verify connection**:
```bash
# Check service status in logs
tail -f logs/markethub.log | grep "ETA"
```

### Holiday Calendar Service

The service auto-initializes with holidays for major markets:

1. **Supported markets**: US, UK, JP, EU, HK, SG, AU, CA, CH, AE, SA

2. **Check if today is a trading day**:
- Service automatically determines trading days
- Handles different weekend days (Middle East: Fri-Sat)
- Accounts for market holidays

3. **Verify holiday data**:
```sql
-- In H2 console (http://localhost:8080/api/h2-console)
SELECT * FROM holidays WHERE market = 'US' ORDER BY holiday_date;
```

4. **Add custom holiday** (via SQL):
```sql
INSERT INTO holidays (market, holiday_year, holiday_date, holiday_name, holiday_type, is_full_day, active)
VALUES ('US', 2025, '2025-12-31', 'New Years Eve', 'SPECIAL', true, true);
```

### Interpolation Service

The service provides multiple methods for filling market data gaps:

1. **Available methods**:
- **LINEAR**: Straight line between points (default)
- **CUBIC**: Smooth spline interpolation
- **TIME_WEIGHTED**: Weights by time distance
- **LAST_VALUE**: Carries forward last value

2. **Configuration**:
```properties
markethub.interpolation.enabled=true
markethub.interpolation.method=LINEAR
markethub.interpolation.max-gap-minutes=5
```

3. **Test interpolation**:
```bash
# Generate data with gaps
curl -X POST http://localhost:8080/api/hub/marketdata/test/AAPL
# Wait 30 seconds
curl -X POST http://localhost:8080/api/hub/marketdata/test/AAPL

# Service automatically interpolates when requesting intermediate timestamps
```

## Complete Test Workflow

### 1. Start with Test Profile
```bash
./gradlew.bat bootRun --args='--spring.profiles.active=test'
```

### 2. Run Complete Test Suite
```bash
# Create test script
cat > complete-test.bat << 'EOF'
@echo off
echo Testing Market Data Hub Services...
echo.

echo 1. Health Check
curl -s http://localhost:8080/api/hub/health
echo.

echo 2. FIX Info
curl -s http://localhost:8080/api/hub/fix/info
echo.

echo 3. Create Market Makers
curl -s -X POST http://localhost:8080/api/hub/marketmakers -H "Content-Type: application/json" -d "{\"marketMakerId\":\"RA\",\"name\":\"Risk Analytics\",\"active\":true}"
curl -s -X POST http://localhost:8080/api/hub/marketmakers -H "Content-Type: application/json" -d "{\"marketMakerId\":\"CMF\",\"name\":\"Capital Markets\",\"active\":true}"
echo.

echo 4. Generate Test Data
curl -s -X POST http://localhost:8080/api/hub/marketdata/test/AAPL
curl -s -X POST http://localhost:8080/api/hub/marketdata/test/GOOGL
curl -s -X POST http://localhost:8080/api/hub/marketdata/test/MSFT
echo.

echo 5. Retrieve Market Data
curl -s http://localhost:8080/api/hub/marketdata
echo.

echo Test Complete!
EOF

complete-test.bat
```

## Service Status Verification

Check all services are running:

```bash
# Check logs for each service
grep "Initializing" logs/markethub.log

# Expected output:
# Initializing FIX Service
# Initializing Holiday Calendar Service
# Initializing Refinitiv ETA Service
# Initializing Hazelcast Configuration Service
```

## Next Steps

After successful testing:

1. **Production Configuration**: Set up real PostgreSQL and Refinitiv credentials
2. **Connect Market Makers**: Configure actual RA/CMF systems via FIX
3. **Enable Refinitiv ETA**: Connect to real market data feed
4. **Load Holiday Data**: Import complete holiday calendars
5. **Performance Tuning**: Optimize based on load testing results