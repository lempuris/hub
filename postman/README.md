# Market Data Hub - Postman Collection

This directory contains Postman collection and environment files for testing the Market Data Hub V1 API.

## Files

- **MarketHub-Collection.postman_collection.json** - Complete API collection with all endpoints
- **MarketHub-Environment.postman_environment.json** - Environment variables for local testing

## Setup Instructions

### 1. Import into Postman

1. Open Postman
2. Click "Import" button (top left)
3. Select both JSON files:
   - `MarketHub-Collection.postman_collection.json`
   - `MarketHub-Environment.postman_environment.json`
4. Click "Import"

### 2. Select Environment

1. In Postman, click the environment dropdown (top right)
2. Select "MarketHub Environment"

### 3. Start the Application

Before running tests, ensure the Market Data Hub is running:

```bash
# Windows
gradlew.bat bootRun --args='--spring.profiles.active=test'

# Linux/Mac
./gradlew bootRun --args='--spring.profiles.active=test'
```

## Collection Structure

The collection is organized into the following sections:

### 1. Health & Monitoring
- Health status checks
- Detailed status information
- Actuator endpoints
- Prometheus metrics

### 2. Market Maker Management
- Create RA and CMF market makers
- List, update, and delete market makers
- Activate/deactivate market makers

### 3. Market Data
- Generate test market data for various symbols
- Retrieve cached market data
- Bulk data operations

### 4. FIX Management
- Send test messages to FIX sessions
- Disconnect sessions
- Restart FIX acceptor

### 5. Cache Management
- Clear local and Hazelcast caches
- Get cache statistics
- Reload configurations

### 6. Statistics
- Reset service statistics

### 7. Test Scenarios
- **Full Test Flow** - Complete end-to-end test
- **Load Test** - Performance testing scenarios

## Running Tests

### Quick Test
1. Run "Get Health Status" to verify the hub is running
2. Run requests in "Full Test Flow" folder sequentially
3. Check results in Postman's Test Results tab

### Full Test Suite
1. Open Collection Runner (Runner button in Postman)
2. Select "Market Data Hub V1" collection
3. Select "MarketHub Environment"
4. Set iterations to 1
5. Click "Run Market Data Hub V1"

### Load Testing
1. Open Collection Runner
2. Select only "Generate 10 Symbols" request
3. Set iterations to 10-100
4. Set delay to 100ms
5. Run to generate load

## Environment Variables

The environment includes the following variables:

| Variable | Default Value | Description |
|----------|--------------|-------------|
| `base_url` | `http://localhost:8080/api` | Base URL for the API |
| `hub_endpoint` | `{{base_url}}/hub` | Hub API endpoint |
| `actuator_endpoint` | `{{base_url}}/actuator` | Spring Actuator endpoint |
| `market_maker_id` | (empty) | Dynamically set during tests |
| `test_symbol` | `AAPL` | Default symbol for testing |
| `fix_port` | `9876` | FIX acceptor port |

## Test Assertions

Each request includes test scripts that verify:
- Response status codes
- Response structure
- Data integrity
- Component health

Failed tests will appear in red in the Test Results tab.

## Common Use Cases

### 1. Initial Setup
Run these requests in order:
1. Get Health Status
2. Create RA Market Maker
3. Create CMF Market Maker
4. List All Market Makers

### 2. Market Data Testing
1. Generate Test Data - AAPL
2. Generate Test Data - GOOGL
3. Get All Market Data
4. Get Market Data - AAPL

### 3. FIX Testing
1. Create market makers first
2. Send Test Message to RA
3. Check Status for FIX session info

### 4. Performance Testing
1. Use Collection Runner
2. Select "Generate 10 Symbols"
3. Run with 50-100 iterations
4. Monitor response times

## Troubleshooting

### Connection Refused
- Verify the application is running on port 8080
- Check the base_url in environment variables

### 404 Not Found
- Ensure you're using the correct profile: `--spring.profiles.active=test`
- Verify the context path is `/api`

### Market Maker Not Found
- Run "Create RA Market Maker" first
- Check that market_maker_id is set in environment

### FIX Errors
- FIX acceptor must be running (check health endpoint)
- Market makers must be created before sending test messages

## Advanced Features

### Custom Symbols
Edit the `test_symbol` environment variable to test different symbols

### Different Environments
Duplicate the environment and modify `base_url` for different deployments:
- Development: `http://localhost:8080/api`
- Staging: `http://staging.markethub.com/api`
- Production: `http://prod.markethub.com/api`

### Automated Testing
Use Newman (Postman CLI) for CI/CD integration:
```bash
newman run MarketHub-Collection.postman_collection.json \
  -e MarketHub-Environment.postman_environment.json
```

## Notes

- All timestamps are in ISO 8601 format
- Prices are in decimal format with up to 6 decimal places
- Market maker IDs must be unique
- FIX sessions require market makers to be created first
- Cache is cleared on application restart

## Support

For issues or questions about the API:
- Check application logs: `logs/markethub.log`
- Review FIX logs: `logs/fix/`
- Monitor health endpoint: `GET /api/hub/health`