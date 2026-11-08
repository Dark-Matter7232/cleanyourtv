@echo off
rem Double-click this file, enter the TV address, and find the PNG in shots\.
set "TV_HOST="
if exist "%~dp0tv-host.txt" set /p TV_HOST=<"%~dp0tv-host.txt"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\capture-screenshots.ps1" %*
exit /b %errorlevel%
