@echo off
setlocal
cd /d "%~dp0"

if "%SBNZ_WEB_API_HOST_PORT%"=="" set "SBNZ_WEB_API_HOST_PORT=8080"

where docker >nul 2>nul
if %errorlevel% neq 0 (
  echo Docker is not available in PATH.
  echo Install Docker Desktop and run this file again.
  pause
  exit /b 1
)

netstat -ano | findstr /r /c:":%SBNZ_WEB_API_HOST_PORT% .*LISTENING" >nul
if %errorlevel% equ 0 (
  echo Port %SBNZ_WEB_API_HOST_PORT% is already in use on this machine.
  echo Set SBNZ_WEB_API_HOST_PORT to a free port, for example:
  echo   set SBNZ_WEB_API_HOST_PORT=8081
  echo Then run this file again.
  pause
  exit /b 1
)

echo Starting SBNZ web stack...
docker compose up -d --build postgres web-api web-frontend
if %errorlevel% neq 0 (
  echo Web stack startup failed.
  pause
  exit /b 1
)

echo Web stack is starting in Docker.
echo Angular frontend: http://localhost:4200
echo Web API:          http://localhost:%SBNZ_WEB_API_HOST_PORT%/api/health
echo To watch logs run: docker compose logs -f web-api web-frontend
pause
