#!/bin/bash

echo "========================================"
echo "Market Data Hub - Postman Tests"
echo "========================================"
echo

# Check if Newman is installed
if ! command -v newman &> /dev/null; then
    echo "Newman is not installed!"
    echo "Please install Newman globally: npm install -g newman"
    exit 1
fi

echo "Running Postman collection tests..."
echo

# Run the collection with the environment
newman run MarketHub-Collection.postman_collection.json \
    -e MarketHub-Environment.postman_environment.json \
    --reporters cli,json \
    --reporter-json-export test-results.json

echo
echo "========================================"
echo "Test execution completed!"
echo "Results saved to: test-results.json"
echo "========================================"