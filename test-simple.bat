@echo off
echo ================================================
echo Market Data Hub V1 - Simple Test
echo ================================================

set BASE_URL=http://localhost:8080/api/hub

echo.
echo 1. Starting the application...
start /B gradlew.bat bootRun --args="--spring.profiles.active=test" > app.log 2>&1

echo Waiting for application to start (15 seconds)...
timeout /t 15 /nobreak > nul

echo.
echo 2. Checking Health...
curl -s "%BASE_URL%/health"

echo.
echo.
echo Application is running! 
echo.
echo You can now test the following:
echo - Health: curl %BASE_URL%/health
echo - H2 Console: http://localhost:8080/api/h2-console (user: sa, no password)
echo - Generate test data: curl -X POST %BASE_URL%/marketdata/test/AAPL
echo.
echo Press any key to stop the application...
pause > nul

echo.
echo Stopping application...
taskkill /F /IM java.exe > nul 2>&1
echo Application stopped.