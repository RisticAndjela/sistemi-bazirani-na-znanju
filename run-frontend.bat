@echo off
setlocal
cd /d "%~dp0"

where docker >nul 2>nul
if %errorlevel% equ 0 (
  echo Starting PostgreSQL in Docker on localhost:5432 ...
  docker compose up -d postgres
  if %errorlevel% neq 0 (
    echo PostgreSQL startup failed.
    pause
    exit /b 1
  )
) else (
  echo Docker is not available in PATH.
  echo Continuing without auto-starting PostgreSQL.
)

cd /d "%~dp0\frontend"

set "PROJECT_JAVA_HOME=C:\Program Files\Microsoft\jdk-11.0.16.101-hotspot"
if exist "%PROJECT_JAVA_HOME%\bin\java.exe" (
  set "JAVA_HOME=%PROJECT_JAVA_HOME%"
  set "PATH=%JAVA_HOME%\bin;%PATH%"
  echo Using project Java from %PROJECT_JAVA_HOME%
) else (
  echo Project JDK 11 was not found at:
  echo   %PROJECT_JAVA_HOME%
  echo Falling back to Java from PATH.
)

where mvn >nul 2>nul
if %errorlevel% neq 0 (
  echo Maven is not available in PATH.
  echo Install Maven and reopen terminal, then run this file again.
  pause
  exit /b 1
)

echo Desktop Swing app still runs locally; only PostgreSQL is managed through Docker.
echo Launching desktop frontend app...
mvn compile
if %errorlevel% neq 0 (
  echo Frontend compile failed.
  echo Make sure backend was built first by running run-backend.bat.
  pause
  exit /b 1
)

mvn exec:java "-Dexec.mainClass=com.sbnz.frontend.DesktopApp"
if %errorlevel% neq 0 (
  echo Frontend launch failed.
  echo Make sure backend was built first by running run-backend.bat.
  pause
  exit /b 1
)
