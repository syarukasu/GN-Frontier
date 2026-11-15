@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0GN-Frontier-Setup\setup.ps1" %*
exit /b %errorlevel%
