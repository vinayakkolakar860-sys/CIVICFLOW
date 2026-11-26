#!/usr/bin/env bash
set -euo pipefail

echo "===================================================================="
echo " CIVICFLOW — Election Management Suite (CEMS) : Launch Application"
echo "===================================================================="

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

if [[ ! -f "config/application.properties" && -z "${CIVICFLOW_DB_USER:-}" ]]; then
    echo "[INFO] Neither config/application.properties nor CIVICFLOW_DB_USER is set."
    echo "       Copy config/application.properties.example to config/application.properties"
    echo "       or export CIVICFLOW_DB_URL, CIVICFLOW_DB_USER, CIVICFLOW_DB_PASSWORD."
fi

SHADED_JAR="target/civicflow-cems-1.0.0-shaded.jar"
if [[ -f "$SHADED_JAR" ]]; then
    echo "Starting CIVICFLOW from $SHADED_JAR ..."
    exec java -jar "$SHADED_JAR"
elif command -v mvn >/dev/null 2>&1; then
    echo "Starting CIVICFLOW via Maven JavaFX plugin..."
    exec mvn javafx:run
else
    echo "[ERROR] Shaded JAR not found and Maven is not installed. Run ./scripts/build-linux-macos.sh first."
    exit 1
fi
