@echo off
setlocal
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

echo Make sure PostgreSQL is running first with run-postgres.bat.
echo Starting web API on http://localhost:8080 ...
mvn clean compile exec:java "-Dexec.mainClass=com.sbnz.frontend.WebApiApp"
if %errorlevel% neq 0 (
  echo Web API launch failed.
  echo Make sure backend artifacts were built first by running run-backend.bat.
  pause
  exit /b 1
)
