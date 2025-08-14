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

## Next Steps

After successful testing:

1. **Connect Real Market Makers**: Configure actual RA/CMF systems to connect via FIX
2. **Integrate Refinitiv**: Implement ETA connector for real market data
3. **Add Holiday Calendar**: Load holiday data for business day calculations
4. **Implement Interpolation**: Add interpolation service for price calculations
5. **Production Deployment**: Configure for production environment with real PostgreSQL