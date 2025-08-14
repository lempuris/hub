# Market Data Hub V1

A lightweight, pass-through FIX engine for distributing market data from Refinitiv to market makers (Risk Analytics and Capital Markets Framework).

## Overview

Market Data Hub V1 is designed as a minimal-logic distribution system that:
- Receives market data from Refinitiv (via ETA protocol)
- Distributes data to market makers via FIX 4.4 protocol
- Manages configurations through Hazelcast distributed cache
- Provides holiday calendar support for date calculations
- Offers advanced interpolation capabilities for gap filling
- Maintains market holiday calendars for multiple regions

## Architecture Highlights

- **Pass-through Design**: Minimal business logic in the hub
- **Configuration-Driven**: Add new instruments without code changes
- **Distributed**: Hazelcast enables horizontal scaling
- **Standards-Based**: Uses FIX 4.4 protocol instead of custom models
- **Non-Persistent**: Market data cached for maximum 1 day (debugging only)

## Prerequisites

- Java 22 or higher
- PostgreSQL 12+ (for operational data)
- Optional: TimescaleDB extension (for time-series market data)
- Network access for Hazelcast clustering (ports 5701-5710)
- FIX connectivity on port 9876

## Quick Start

### 1. Database Setup

Create a PostgreSQL database:
```sql
CREATE DATABASE markethub;
CREATE USER markethub WITH PASSWORD 'markethub';
GRANT ALL PRIVILEGES ON DATABASE markethub TO markethub;
```

### 2. Build the Application

```bash
# Windows
gradlew.bat clean build

# Linux/Mac
./gradlew clean build
```

### 3. Run the Application

```bash
# Windows
gradlew.bat bootRun

# Linux/Mac
./gradlew bootRun
```

The application will start with:
- Web server on http://localhost:8080/api
- FIX acceptor on port 9876
- Hazelcast on port 5701

## Configuration

Key configuration in `src/main/resources/application.properties`:

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/markethub
spring.datasource.username=markethub
spring.datasource.password=markethub

# FIX Protocol
fix.acceptorPort=9876
fix.senderCompId=MARKETHUB

# Hazelcast
hazelcast.cluster.name=markethub-cluster
hazelcast.network.port=5701
```

## API Endpoints

### Health & Monitoring

- `GET /api/hub/health` - Overall health status
- `GET /api/hub/status` - Detailed status information
- `GET /api/actuator/health` - Spring Boot health check
- `GET /api/actuator/prometheus` - Prometheus metrics

### Market Maker Management

- `GET /api/hub/marketmakers` - List all market makers
- `POST /api/hub/marketmakers` - Create new market maker
- `PUT /api/hub/marketmakers/{id}` - Update market maker
- `DELETE /api/hub/marketmakers/{id}` - Delete market maker
- `POST /api/hub/marketmakers/{id}/activate` - Activate market maker
- `POST /api/hub/marketmakers/{id}/deactivate` - Deactivate market maker

### Market Data Testing

- `GET /api/hub/marketdata/{symbol}` - Get latest data for symbol
- `GET /api/hub/marketdata` - Get all cached market data
- `POST /api/hub/marketdata/test/{symbol}` - Generate test data

### FIX Management

- `GET /api/hub/fix/info` - Get FIX connection information
- `POST /api/hub/fix/restart` - Restart FIX acceptor
- `POST /api/hub/fix/test/{targetCompId}` - Send test message
- `POST /api/hub/fix/disconnect/{targetCompId}` - Disconnect session

### Cache Management

- `POST /api/hub/cache/clear/local` - Clear local market data cache
- `POST /api/hub/cache/clear/hazelcast` - Clear Hazelcast cache
- `GET /api/hub/cache/stats` - Get cache statistics
- `POST /api/hub/cache/reload` - Reload configurations from database

## Testing the Hub

### 1. Create a Market Maker Configuration

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

### 2. Generate Test Market Data

```bash
curl -X POST http://localhost:8080/api/hub/marketdata/test/AAPL
```

### 3. Check Health Status

```bash
curl http://localhost:8080/api/hub/health
```

## FIX Connectivity

Market makers connect to the hub using FIX 4.4 protocol:

- **Protocol**: FIX 4.4
- **Port**: 9876
- **SenderCompID**: MARKETHUB
- **Supported Messages**:
  - Market Data Request (V)
  - Market Data Snapshot/Full Refresh (W)
  - Standard admin messages (Logon, Heartbeat, etc.)

### Sample FIX Configuration for Market Makers

```ini
[DEFAULT]
ConnectionType=initiator
BeginString=FIX.4.4
TargetCompID=MARKETHUB
HeartBtInt=30
ReconnectInterval=5

[SESSION]
SenderCompID=RA
SocketConnectHost=localhost
SocketConnectPort=9876
```

## Project Structure

```
markethub/
├── src/main/java/com/refinitiv/markethub/
│   ├── config/          # Configuration classes
│   ├── controller/      # REST API endpoints
│   ├── fix/            # FIX protocol handling
│   ├── model/          # Domain entities
│   ├── repository/     # Database repositories
│   └── service/        # Business logic
├── src/main/resources/
│   └── application.properties
├── build.gradle        # Build configuration
└── CLAUDE.md          # AI assistant guidance
```

## Monitoring

The application exposes Prometheus metrics at:
- `/api/actuator/prometheus`

Health checks available at:
- `/api/actuator/health`
- `/api/hub/health` (custom detailed health)

## Development Notes

### Running Tests

```bash
# All tests
gradlew test

# Specific test class
gradlew test --tests "com.refinitiv.markethub.MarkethubApplicationTests"
```

### Debugging

1. **Enable debug logging** in `application.properties`:
   ```properties
   logging.level.com.refinitiv.markethub=DEBUG
   logging.level.quickfix=DEBUG
   ```

2. **FIX message logs** are stored in `logs/fix/`

3. **Application logs** are in `logs/markethub.log`

## Testing New Services

### Refinitiv ETA Service Testing

The Refinitiv ETA service is disabled by default. To test it:

1. **Enable in application.properties**:
   ```properties
   refinitiv.eta.enabled=true
   refinitiv.eta.host=elektron.refinitiv.com
   refinitiv.eta.port=14002
   refinitiv.eta.username=YOUR_USERNAME
   refinitiv.eta.service=ELEKTRON_DD
   ```

2. **Verify connection** by checking logs:
   ```
   grep "Refinitiv ETA" logs/markethub.log
   ```

3. **Simulate market data** (for testing without credentials):
   - The service includes simulation mode for testing
   - Generates realistic market data for configured symbols

### Holiday Calendar Service Testing

The holiday calendar service initializes with default holidays for major markets:

1. **Check supported markets**:
   ```bash
   # Markets include: US, UK, JP, EU, HK, SG, AU, CA, CH, AE, SA
   ```

2. **Test holiday checking**:
   - Service automatically loads holidays for current year
   - Supports different weekend days for Middle East markets
   - Provides trading day calculations

3. **Verify holiday data** in database:
   ```sql
   SELECT * FROM holidays WHERE market = 'US' AND holiday_year = 2025;
   ```

### Interpolation Service Testing

The interpolation service provides multiple methods for filling data gaps:

1. **Available methods**:
   - LINEAR: Linear interpolation between points
   - CUBIC: Smooth cubic spline interpolation
   - TIME_WEIGHTED: Time-weighted average
   - LAST_VALUE: Carry forward last known value

2. **Configuration**:
   ```properties
   markethub.interpolation.enabled=true
   markethub.interpolation.method=LINEAR
   markethub.interpolation.max-gap-minutes=5
   ```

3. **Testing interpolation**:
   - Generate test data with gaps
   - Service automatically fills gaps when data is requested
   - Maximum gap of 5 minutes by default

### Test with H2 Database (Development)

For quick testing without PostgreSQL:

```bash
# Windows
gradlew.bat bootRun --args='--spring.profiles.active=test'

# Linux/Mac
./gradlew bootRun --args='--spring.profiles.active=test'
```

## Completed Features (V1)

- ✅ FIX 4.4 acceptor implementation
- ✅ Hazelcast distributed configuration
- ✅ Market maker management
- ✅ Refinitiv ETA integration module (disabled by default)
- ✅ Holiday calendar service with major markets
- ✅ Advanced interpolation logic with multiple methods
- ✅ RESTful API for management
- ✅ Prometheus metrics integration

## Pending Features (V2)

- [ ] TimescaleDB integration for market data persistence
- [ ] Web UI for configuration management
- [ ] Advanced alerting and monitoring
- [ ] Market data replay functionality

## Team Assignments

- **Dustin**: Lock-free IPC framework, repository setup, interpolation logic
- **Lazola**: ETA (Reuters) integration work
- **Musa**: Front-end development (market data dashboards, config UI)
- **Benefactor**: Assist Musa; handle holidays & operational DB tasks

## License

Proprietary - Refinitiv

## Support

For issues or questions, contact the Market Data Hub team.