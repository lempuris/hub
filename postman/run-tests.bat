@echo off
echo ========================================
echo Market Data Hub - Postman Tests
echo ========================================
echo.

REM Check if Newman is installed
where newman >nul 2>nul
if %errorlevel% neq 0 (
    echo Newman is not installed!
    echo Please install Newman globally: npm install -g newman
    exit /b 1
)

echo Running Postman collection tests...
echo.

REM Run the collection with the environment
newman run MarketHub-Collection.postman_collection.json ^
    -e MarketHub-Environment.postman_environment.json ^
    --reporters cli,json ^
    --reporter-json-export test-results.json

echo.
echo ========================================
echo Test execution completed!
echo Results saved to: test-results.json
echo ========================================