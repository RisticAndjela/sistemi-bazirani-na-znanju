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

echo Building Docker image that includes backend artifacts...
docker compose build web-api
if %errorlevel% neq 0 (
  echo Docker build failed.
  pause
  exit /b 1
)

echo Web API image was built successfully.
echo Backend modules are now packaged inside the Docker image.
pause
