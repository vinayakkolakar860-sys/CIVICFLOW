#!/usr/bin/env bash
set -euo pipefail

echo "===================================================================="
echo " CIVICFLOW — Election Management Suite (CEMS) : Linux/macOS Build"
echo "===================================================================="

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

if ! command -v mvn >/dev/null 2>&1; then
    echo "[ERROR] Apache Maven (mvn) not found on PATH. Please install Maven 3.9+."
    exit 1
fi

mvn clean package
echo ""
echo "[SUCCESS] Build complete. Runnable JAR: target/civicflow-cems-1.0.0-shaded.jar"
