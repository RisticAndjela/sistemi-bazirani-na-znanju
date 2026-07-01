@echo off
setlocal
cd /d "%~dp0"

where docker >nul 2>nul
if %errorlevel% neq 0 (
  echo Docker is not available in PATH.
  echo Install Docker Desktop and run this file again.
  pause
  exit /b 1
)

echo Starting Angular web frontend in Docker on http://localhost:4200 ...
docker compose up -d --build postgres web-api web-frontend
if %errorlevel% neq 0 (
  echo Angular frontend startup failed.
  pause
  exit /b 1
)

echo Angular container is starting.
echo Open http://localhost:4200
echo To watch logs run: docker compose logs -f web-frontend web-api
pause
