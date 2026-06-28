@echo off
setlocal
cd /d "%~dp0"

echo Starting SBNZ web stack...
start "SBNZ Web API" cmd /k call "%~dp0run-web-api.bat"
start "SBNZ Angular Frontend" cmd /k call "%~dp0run-web-frontend.bat"

echo Web API terminal and Angular terminal were opened.
echo Wait for both to finish booting, then open http://localhost:4200
