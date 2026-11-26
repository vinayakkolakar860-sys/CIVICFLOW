@echo off
setlocal
echo ====================================================================
echo  CIVICFLOW - Election Management Suite (CEMS) : Windows Build
echo ====================================================================

cd /d "%~dp0.."

where mvn >nul 2>nul
if %errorlevel%==0 (
    set "MVN_CMD=mvn"
) else if exist "%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.9\977a63e90f436cd6ade95b4c0e10c20c\bin\mvn.cmd" (
    set "MVN_CMD=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.9\977a63e90f436cd6ade95b4c0e10c20c\bin\mvn.cmd"
) else (
    echo [ERROR] Apache Maven was not found in PATH. Please install Maven 3.9+ and ensure mvn is on PATH.
    exit /b 1
)

echo Running clean package and automated test suite...
call "%MVN_CMD%" clean package
if %errorlevel% neq 0 (
    echo [ERROR] Build failed. Check the output above.
    exit /b %errorlevel%
)

echo.
echo [SUCCESS] Build complete. Runnable JAR: target\civicflow-cems-1.0.0-shaded.jar
endlocal
