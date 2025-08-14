@echo off
echo ================================================
echo Market Data Hub - FIX Connection Test
echo ================================================
echo.

set BASE_URL=http://localhost:8080/api/hub

echo Checking FIX Information...
echo.
curl -s "%BASE_URL%/fix/info" | python -m json.tool

echo.
echo ------------------------------------------------
echo.
echo Testing FIX Message Send (Expected to fail without connection)...
echo.
curl -s -X POST "%BASE_URL%/fix/test/CMF" | python -m json.tool

echo.
echo ================================================
echo.
echo EXPLANATION:
echo ------------
echo The FIX acceptor is running and waiting for connections on port 9876.
echo Since this is an ACCEPTOR, it waits for market makers to connect TO it.
echo.
echo To connect a market maker, you need a FIX initiator with:
echo   - Host: localhost
echo   - Port: 9876
echo   - TargetCompID: MARKETHUB
echo   - SenderCompID: RA or CMF
echo   - BeginString: FIX.4.4
echo.
echo Without actual FIX clients connected, test messages will show as "failed"
echo but this is expected behavior - the hub is working correctly!
echo ================================================