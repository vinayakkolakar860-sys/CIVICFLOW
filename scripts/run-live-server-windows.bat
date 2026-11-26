@echo off
setlocal
echo ====================================================================
echo  CIVICFLOW - Election Management Suite (CEMS) : Live Web Server
echo ====================================================================

cd /d "%~dp0.."

set "SHADED_JAR=target\civicflow-cems-1.0.0-shaded.jar"
if not exist "%SHADED_JAR%" (
    echo [ERROR] Build not found. Run scripts\build-windows.bat first.
    exit /b 1
)

echo Starting CIVICFLOW Embedded Core Java Live Server on http://localhost:8080 ...
java -cp "%SHADED_JAR%" com.civicflow.web.CivicFlowWebServer 8080
endlocal
