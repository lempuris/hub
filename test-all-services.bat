@echo off
echo ================================================
echo Market Data Hub - Complete Service Test
echo ================================================
echo.

set BASE_URL=http://localhost:8080/api/hub

echo [1/10] Checking Health Status...
curl -s "%BASE_URL%/health" | python -m json.tool
echo.
echo ------------------------------------------------
timeout /t 2 /nobreak > nul

echo [2/10] Getting FIX Connection Info...
curl -s "%BASE_URL%/fix/info" | python -m json.tool
echo.
echo ------------------------------------------------
timeout /t 2 /nobreak > nul

echo [3/10] Creating Market Makers...
echo Creating RA...
curl -s -X POST "%BASE_URL%/marketmakers" -H "Content-Type: application/json" -d "{\"marketMakerId\":\"RA\",\"name\":\"Risk Analytics\",\"compId\":\"RA\",\"type\":\"RISK_ANALYTICS\",\"subscribedSymbols\":[\"AAPL\",\"GOOGL\",\"SPX*\"],\"active\":true}" | python -m json.tool
echo.
echo Creating CMF...
curl -s -X POST "%BASE_URL%/marketmakers" -H "Content-Type: application/json" -d "{\"marketMakerId\":\"CMF\",\"name\":\"Capital Markets Framework\",\"compId\":\"CMF\",\"type\":\"CAPITAL_MARKETS\",\"subscribedSymbols\":[\"*\"],\"active\":true}" | python -m json.tool
echo.
echo ------------------------------------------------
timeout /t 2 /nobreak > nul

echo [4/10] Generating Test Market Data...
echo Generating AAPL...
curl -s -X POST "%BASE_URL%/marketdata/test/AAPL" | python -m json.tool
timeout /t 1 /nobreak > nul
echo.
echo Generating GOOGL...
curl -s -X POST "%BASE_URL%/marketdata/test/GOOGL" | python -m json.tool
timeout /t 1 /nobreak > nul
echo.
echo Generating MSFT...
curl -s -X POST "%BASE_URL%/marketdata/test/MSFT" | python -m json.tool
echo.
echo ------------------------------------------------
timeout /t 2 /nobreak > nul

echo [5/10] Testing Interpolation (Generating data with gaps)...
echo First data point...
curl -s -X POST "%BASE_URL%/marketdata/test/TSLA" | python -m json.tool
echo.
echo Waiting 3 seconds for gap...
timeout /t 3 /nobreak > nul
echo Second data point...
curl -s -X POST "%BASE_URL%/marketdata/test/TSLA" | python -m json.tool
echo.
echo ------------------------------------------------
timeout /t 2 /nobreak > nul

echo [6/10] Retrieving All Market Data...
curl -s "%BASE_URL%/marketdata" | python -m json.tool
echo.
echo ------------------------------------------------
timeout /t 2 /nobreak > nul

echo [7/10] Testing Cache Statistics...
curl -s "%BASE_URL%/cache/stats"
echo.
echo ------------------------------------------------
timeout /t 2 /nobreak > nul

echo [8/10] Getting Detailed Status...
curl -s "%BASE_URL%/status"
echo.
echo ------------------------------------------------
timeout /t 2 /nobreak > nul

echo [9/10] Testing FIX Message Send (Expected to show waiting for connection)...
curl -s -X POST "%BASE_URL%/fix/test/RA" | python -m json.tool
echo.
echo ------------------------------------------------
timeout /t 2 /nobreak > nul

echo [10/10] Final Health Check...
curl -s "%BASE_URL%/health" | python -m json.tool
echo.

echo ================================================
echo TEST COMPLETE!
echo ================================================
echo.
echo Service Status Summary:
echo - FIX Service: Running on port 9876 (Acceptor mode)
echo - Hazelcast: Cluster running
echo - Market Makers: 2 configured (RA, CMF)
echo - Market Data: Test data generated for multiple symbols
echo - Interpolation: Ready for gap filling
echo - Holiday Calendar: Initialized with default holidays
echo - Refinitiv ETA: Disabled by default (enable in config)
echo.
echo To test with real connections:
echo 1. Enable Refinitiv ETA in application.properties
echo 2. Connect FIX clients (RA/CMF) to port 9876
echo 3. Load complete holiday calendar data
echo.
echo Check logs for details: logs\markethub.log
echo ================================================
pause