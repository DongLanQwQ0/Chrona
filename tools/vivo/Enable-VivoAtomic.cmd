@echo off
where pwsh.exe >nul 2>nul
if errorlevel 1 (
  echo PowerShell 7 is required. Install it from https://aka.ms/powershell-release
  pause
  exit /b 1
)
pwsh.exe -NoProfile -File "%~dp0Configure-VivoAtomicScene.ps1" -Mode Enable %*
set "result=%errorlevel%"
if not "%result%"=="0" echo Setup failed. Read the error above; no success was confirmed.
pause
exit /b %result%
