#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
EXPECTED_SHA="${2:-}"

echo "Running smoke test against $BASE_URL..."

# 1. Check liveness
echo "Checking liveness..."
LIVENESS=$(curl -fsS "$BASE_URL/actuator/health/liveness")
echo "$LIVENESS" | grep -q '"status":"UP"' || (echo "Liveness check failed: $LIVENESS" && exit 1)

# 2. Check readiness
echo "Checking readiness..."
READINESS=$(curl -fsS "$BASE_URL/actuator/health/readiness")
echo "$READINESS" | grep -q '"status":"UP"' || (echo "Readiness check failed: $READINESS" && exit 1)

# 3. Check version
echo "Checking version endpoint..."
VERSION_JSON=$(curl -fsS "$BASE_URL/api/v1/version")
echo "Version response: $VERSION_JSON"

if [ -n "$EXPECTED_SHA" ]; then
  echo "$VERSION_JSON" | grep -q "$EXPECTED_SHA" || (echo "Expected SHA $EXPECTED_SHA not found in $VERSION_JSON" && exit 1)
fi

echo "Smoke test passed successfully!"
