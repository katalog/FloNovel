@echo off
setlocal
cd /d "%~dp0"

echo ========================================================
echo  FloNovel Desktop - EXE Build
echo ========================================================

call gradlew.bat packageExe createDistributable

if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Build failed with exit code %ERRORLEVEL%
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ========================================================
echo  BUILD SUCCESSFUL
echo ========================================================
echo [1] Installer EXE:
echo     %~dp0build\compose\binaries\main\exe\flonovel-1.0.0.exe
echo.
echo [2] Portable Standalone App (flonovel.exe):
echo     %~dp0build\compose\binaries\main\app\flonovel\flonovel.exe
echo ========================================================
echo.
pause
