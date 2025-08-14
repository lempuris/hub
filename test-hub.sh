#!/bin/bash

# Test script for Market Data Hub
# This script tests the core functionality of the hub

BASE_URL="http://localhost:8080/api/hub"

echo "================================================"
echo "Market Data Hub V1 - Test Suite"
echo "================================================"

# Function to check if service is running
check_service() {
    echo -n "Checking if hub is running... "
    if curl -s "${BASE_URL}/health" > /dev/null 2>&1; then
        echo "✓ Hub is running"
        return 0
    else
        echo "✗ Hub is not running"
        echo "Please start the application first with: ./gradlew bootRun"
        return 1
    fi
}

# Function to print JSON response nicely
print_response() {
    echo "$1" | python3 -m json.tool 2>/dev/null || echo "$1"
}

# 1. Check health
test_health() {
    echo ""
    echo "1. Testing Health Endpoint"
    echo "--------------------------"
    response=$(curl -s "${BASE_URL}/health")
    print_response "$response"
}

# 2. Create a market maker
test_create_market_maker() {
    echo ""
    echo "2. Creating Market Maker (Risk Analytics)"
    echo "------------------------------------------"
    response=$(curl -s -X POST "${BASE_URL}/marketmakers" \
        -H "Content-Type: application/json" \
        -d '{
            "marketMakerId": "RA",
            "name": "Risk Analytics",
            "compId": "RA",
            "type": "RISK_ANALYTICS",
            "connectionMode": "INITIATOR",
            "port": 9876,
            "heartbeatInterval": 30,
            "subscribedSymbols": ["AAPL", "GOOGL", "SPX*", "MSFT"],
            "active": true,
            "priority": 10,
            "maxMessagesPerSecond": 100
        }')
    print_response "$response"
    
    # Create second market maker
    echo ""
    echo "Creating Market Maker (Capital Markets Framework)"
    echo "-------------------------------------------------"
    response=$(curl -s -X POST "${BASE_URL}/marketmakers" \
        -H "Content-Type: application/json" \
        -d '{
            "marketMakerId": "CMF",
            "name": "Capital Markets Framework",
            "compId": "CMF",
            "type": "CAPITAL_MARKETS",
            "connectionMode": "INITIATOR",
            "port": 9876,
            "heartbeatInterval": 30,
            "subscribedSymbols": ["*"],
            "active": true,
            "priority": 5,
            "maxMessagesPerSecond": 200
        }')
    print_response "$response"
}

# 3. List market makers
test_list_market_makers() {
    echo ""
    echo "3. Listing All Market Makers"
    echo "-----------------------------"
    response=$(curl -s "${BASE_URL}/marketmakers")
    print_response "$response"
}

# 4. Generate test market data
test_generate_market_data() {
    echo ""
    echo "4. Generating Test Market Data"
    echo "-------------------------------"
    
    symbols=("AAPL" "GOOGL" "MSFT" "SPX")
    
    for symbol in "${symbols[@]}"; do
        echo "Generating data for $symbol..."
        response=$(curl -s -X POST "${BASE_URL}/marketdata/test/${symbol}")
        echo "Generated: $(echo $response | python3 -c "import sys, json; data=json.load(sys.stdin); print(f\"{data.get('symbol', 'N/A')}: Bid={data.get('bidPrice', 'N/A')}, Ask={data.get('askPrice', 'N/A')}, Last={data.get('lastPrice', 'N/A')}\")" 2>/dev/null || echo "Error parsing response")"
    done
}

# 5. Get cached market data
test_get_market_data() {
    echo ""
    echo "5. Retrieving Cached Market Data"
    echo "---------------------------------"
    response=$(curl -s "${BASE_URL}/marketdata")
    echo "Cached symbols: $(echo $response | python3 -c "import sys, json; print(', '.join(json.load(sys.stdin).keys()))" 2>/dev/null || echo "No data")"
    
    # Get specific symbol
    echo ""
    echo "Getting AAPL market data:"
    response=$(curl -s "${BASE_URL}/marketdata/AAPL")
    print_response "$response"
}

# 6. Test status endpoint
test_status() {
    echo ""
    echo "6. Getting Detailed Status"
    echo "---------------------------"
    response=$(curl -s "${BASE_URL}/status")
    print_response "$response"
}

# 7. Test FIX functionality
test_fix() {
    echo ""
    echo "7. Testing FIX Functionality"
    echo "-----------------------------"
    echo "Sending test message to RA..."
    response=$(curl -s -X POST "${BASE_URL}/fix/test/RA")
    echo "Response: $response"
}

# Main test execution
main() {
    if ! check_service; then
        exit 1
    fi
    
    test_health
    test_create_market_maker
    test_list_market_makers
    test_generate_market_data
    test_get_market_data
    test_status
    test_fix
    
    echo ""
    echo "================================================"
    echo "Test Suite Completed"
    echo "================================================"
    echo ""
    echo "To monitor the hub:"
    echo "  - Health: curl ${BASE_URL}/health"
    echo "  - Metrics: curl http://localhost:8080/api/actuator/prometheus"
    echo "  - Logs: Check logs/markethub.log"
    echo ""
}

# Run the tests
main