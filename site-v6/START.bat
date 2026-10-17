@echo off
cd /d "%~dp0"
where py >nul 2>nul
if not errorlevel 1 (
  py -3 serve.py
  goto :done
)
where python >nul 2>nul
if not errorlevel 1 (
  python serve.py
  goto :done
)
echo Python not found. Starting the CopiMine v6 offline HTML instead.
call "%~dp0OPEN_DEMO.bat"
:done
pause
