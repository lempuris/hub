# Market Data Hub V1

A lightweight, pass-through FIX engine for distributing market data from Refinitiv to market makers (Risk Analytics and Capital Markets Framework).

## Overview

Market Data Hub V1 is designed as a minimal-logic distribution system that:
- Receives market data from Refinitiv (via ETA protocol)
- Distributes data to market makers via FIX 4.4 protocol
- Manages configurations through Hazelcast distributed cache
- Provides holiday calendar support for date calculations
- Offers simple interpolation capabilities

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

- `POST /api/hub/fix/restart` - Restart FIX acceptor
- `POST /api/hub/fix/test/{targetCompId}` - Send test message
- `POST /api/hub/fix/disconnect/{targetCompId}` - Disconnect session

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

## Pending Features (V1)

- [ ] Refinitiv ETA integration (currently using test data)
- [ ] Holiday calendar service implementation
- [ ] Simple interpolation logic
- [ ] TimescaleDB integration for market data persistence

## Team Assignments

- **Dustin**: Lock-free IPC framework, repository setup, interpolation logic
- **Lazola**: ETA (Reuters) integration work
- **Musa**: Front-end development (market data dashboards, config UI)
- **Benefactor**: Assist Musa; handle holidays & operational DB tasks

## License

Proprietary - Refinitiv

## Support

For issues or questions, contact the Market Data Hub team.