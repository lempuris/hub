# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Development Commands

### Build the project
```bash
# Windows
gradlew.bat build

# Linux/Mac
./gradlew build
```

### Run the application
```bash
# Windows
gradlew.bat bootRun

# Linux/Mac
./gradlew bootRun
```

### Run tests
```bash
# Windows
gradlew.bat test

# Linux/Mac
./gradlew test
```

### Run a specific test class
```bash
# Windows
gradlew.bat test --tests "com.refinitiv.markethub.MarkethubApplicationTests"

# Linux/Mac
./gradlew test --tests "com.refinitiv.markethub.MarkethubApplicationTests"
```

### Clean build
```bash
# Windows
gradlew.bat clean build

# Linux/Mac
./gradlew clean build
```

## Project Architecture

This is a Market Data Hub V1 - a lightweight, pass-through FIX engine for distributing market data from Refinitiv to market makers (RA, CMF).

### Core Structure
- **Main Application**: `com.refinitiv.markethub.MarkethubApplication` - Entry point with async and scheduling enabled
- **Configuration**: 
  - `config/` - Application, FIX, Hazelcast, and Database configurations
  - `application.properties` - All runtime configurations
- **Package Structure**:
  - `model/` - Domain entities (Instrument, MarketData, Holiday, MarketMakerConfig)
  - `service/` - Business logic (FIXService, MarketDataService, MarketMakerService, HazelcastConfigService)
  - `fix/` - FIX protocol handling (MarketDataFIXApplication)
  - `repository/` - JPA repositories for database access

### Technology Stack
- **Core**: Spring Boot 3.5.4, Java 22
- **FIX Protocol**: QuickFIX/J 2.3.1 for market data distribution
- **Distributed Cache**: Hazelcast 5.3.2 for configuration management
- **Database**: PostgreSQL with optional TimescaleDB for time-series data
- **Async Processing**: Project Reactor for reactive streams
- **Monitoring**: Spring Actuator with Prometheus metrics

### Key Components

#### FIX Protocol Setup
- **FIX Acceptor**: Listens on port 9876 for market maker connections
- **Sessions**: Configured for RA and CMF market makers
- **Message Flow**: Market data distributed via MarketDataSnapshotFullRefresh (Type W)

#### Hazelcast Configuration
- **Distributed Maps**:
  - `instruments` - Instrument configurations
  - `marketmakers` - Market maker configurations  
  - `productmappings` - Symbol mapping per market maker
  - `marketdatacache` - 1-hour TTL market data cache
  - `holidaycalendar` - 24-hour TTL holiday cache

#### Market Data Flow
1. Data received from Refinitiv (via ETA - to be implemented)
2. Validated and enriched in MarketDataService
3. Cached in Hazelcast (optional)
4. Distributed to subscribed market makers via FIX

#### Database Schema
- Uses JPA/Hibernate with auto-update DDL
- Entities: Instrument, Holiday, MarketMakerConfig
- PostgreSQL for operational data, optional TimescaleDB for market data

### Key Considerations
- **Pass-through design**: Minimal business logic in hub
- **Configuration-driven**: New instruments without code changes
- **Distributed**: Hazelcast enables horizontal scaling
- **Non-persistent**: Market data not stored long-term (1 day max for debugging)
- **Standards-based**: FIX 4.4 protocol instead of custom models

### Required External Systems
- PostgreSQL database on localhost:5432
- Hazelcast cluster members (configurable)
- Refinitiv data feed (ETA integration pending)