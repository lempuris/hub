@echo off
echo ================================================
echo Market Data Hub V1 - Test Suite
echo ================================================

set BASE_URL=http://localhost:8080/api/hub

echo.
echo Checking if hub is running...
curl -s "%BASE_URL%/health" >nul 2>&1
if %errorlevel% neq 0 (
    echo Hub is not running!
    echo Please start the application first with: gradlew.bat bootRun
    exit /b 1
)
echo Hub is running!

echo.
echo 1. Testing Health Endpoint
echo --------------------------
curl -s "%BASE_URL%/health"

echo.
echo.
echo 2. Creating Market Maker - Risk Analytics
echo ------------------------------------------
curl -s -X POST "%BASE_URL%/marketmakers" -H "Content-Type: application/json" -d "{\"marketMakerId\":\"RA\",\"name\":\"Risk Analytics\",\"compId\":\"RA\",\"type\":\"RISK_ANALYTICS\",\"connectionMode\":\"INITIATOR\",\"port\":9876,\"heartbeatInterval\":30,\"subscribedSymbols\":[\"AAPL\",\"GOOGL\",\"SPX*\",\"MSFT\"],\"active\":true,\"priority\":10,\"maxMessagesPerSecond\":100}"

echo.
echo.
echo Creating Market Maker - Capital Markets Framework
echo -------------------------------------------------
curl -s -X POST "%BASE_URL%/marketmakers" -H "Content-Type: application/json" -d "{\"marketMakerId\":\"CMF\",\"name\":\"Capital Markets Framework\",\"compId\":\"CMF\",\"type\":\"CAPITAL_MARKETS\",\"connectionMode\":\"INITIATOR\",\"port\":9876,\"heartbeatInterval\":30,\"subscribedSymbols\":[\"*\"],\"active\":true,\"priority\":5,\"maxMessagesPerSecond\":200}"

echo.
echo.
echo 3. Listing All Market Makers
echo -----------------------------
curl -s "%BASE_URL%/marketmakers"

echo.
echo.
echo 4. Generating Test Market Data
echo -------------------------------
echo Generating data for AAPL...
curl -s -X POST "%BASE_URL%/marketdata/test/AAPL"
echo.
echo Generating data for GOOGL...
curl -s -X POST "%BASE_URL%/marketdata/test/GOOGL"
echo.
echo Generating data for MSFT...
curl -s -X POST "%BASE_URL%/marketdata/test/MSFT"
echo.
echo Generating data for SPX...
curl -s -X POST "%BASE_URL%/marketdata/test/SPX"

echo.
echo.
echo 5. Getting AAPL Market Data
echo ----------------------------
curl -s "%BASE_URL%/marketdata/AAPL"

echo.
echo.
echo 6. Getting Hub Status
echo ---------------------
curl -s "%BASE_URL%/status"

echo.
echo.
echo ================================================
echo Test Suite Completed
echo ================================================
echo.
echo To monitor the hub:
echo   - Health: curl %BASE_URL%/health
echo   - Metrics: curl http://localhost:8080/api/actuator/prometheus
echo   - Logs: Check logs\markethub.log
echo.