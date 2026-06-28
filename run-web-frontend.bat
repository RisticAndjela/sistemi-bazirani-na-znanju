@echo off
setlocal
cd /d "%~dp0\web-frontend"

where npm.cmd >nul 2>nul
if %errorlevel% neq 0 (
  echo npm.cmd is not available in PATH.
  echo Install Node.js and reopen terminal, then run this file again.
  pause
  exit /b 1
)

if not exist "node_modules" (
  echo Installing Angular dependencies...
  call npm.cmd install
  if %errorlevel% neq 0 (
    echo npm install failed.
    pause
    exit /b 1
  )
)

echo Starting Angular web frontend on http://localhost:4200 ...
call npm.cmd start
if %errorlevel% neq 0 (
  echo Angular frontend failed to start.
  pause
  exit /b 1
)
