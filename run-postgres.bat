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

echo Starting PostgreSQL on localhost:5432...
docker compose up -d postgres
if %errorlevel% neq 0 (
  echo PostgreSQL startup failed.
  pause
  exit /b 1
)

echo PostgreSQL is running.
echo Host: localhost
echo Port: 5432
echo Database: sbnz_respiratory
echo User: sbnz_user
echo Password: sbnz_pass
pause
