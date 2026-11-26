@echo off
setlocal
echo ====================================================================
echo  CIVICFLOW - Election Management Suite (CEMS) : Launch Application
echo ====================================================================

cd /d "%~dp0.."

if not exist "config\application.properties" (
    if "%CIVICFLOW_DB_USER%"=="" (
        echo [INFO] Neither config\application.properties nor CIVICFLOW_DB_USER was detected.
        echo        Copy config\application.properties.example to config\application.properties
        echo        or set CIVICFLOW_DB_URL, CIVICFLOW_DB_USER, and CIVICFLOW_DB_PASSWORD.
    )
)

set "SHADED_JAR=target\civicflow-cems-1.0.0-shaded.jar"
if exist "%SHADED_JAR%" (
    echo Starting CIVICFLOW from %SHADED_JAR% ...
    java -jar "%SHADED_JAR%"
    exit /b %errorlevel%
)

where mvn >nul 2>nul
if %errorlevel%==0 (
    echo Starting CIVICFLOW via Maven JavaFX plugin...
    mvn javafx:run
    exit /b %errorlevel%
)

echo [ERROR] Shaded JAR not found and Maven is not on PATH. Run scripts\build-windows.bat first.
exit /b 1
endlocal
