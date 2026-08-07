#!/usr/bin/env bash
set -euo pipefail

SUPPLIER_PORT=8080
SIMULATOR_PORT=8090
COHORT="integration-test"
PASS=0
FAIL=0

cleanup() {
  [ -n "${SIMULATOR_PID:-}" ] && kill "$SIMULATOR_PID" 2>/dev/null || true
  [ -n "${SUPPLIER_PID:-}" ] && kill "$SUPPLIER_PID" 2>/dev/null || true
}
trap cleanup EXIT

wait_for_port() {
  local port=$1 label=$2
  echo "Waiting for $label on port $port..."
  for i in $(seq 1 120); do
    nc -z localhost "$port" 2>/dev/null && return 0 || true
    sleep 0.5
  done
  echo "Timed out waiting for $label" >&2
  return 1
}

assert_status() {
  local label=$1 expected=$2 actual=$3
  if [ "$actual" -eq "$expected" ]; then
    echo "  PASS  $label"
    PASS=$((PASS + 1))
  else
    echo "  FAIL  $label (expected $expected, got $actual)"
    FAIL=$((FAIL + 1))
  fi
}

echo "=== Building ==="
(cd examples/reference-supplier  && ./gradlew bootJar -q)
(cd examples/healthstore-simulator && ./gradlew bootJar -q)

SUPPLIER_JAR=$(ls examples/reference-supplier/build/libs/*.jar | grep -v plain)
SIMULATOR_JAR=$(ls examples/healthstore-simulator/build/libs/*.jar | grep -v plain)

echo ""
echo "=== Starting services ==="

java -jar "$SUPPLIER_JAR"  > /tmp/reference-supplier.log  2>&1 &
SUPPLIER_PID=$!

java -jar "$SIMULATOR_JAR" > /tmp/healthstore-simulator.log 2>&1 &
SIMULATOR_PID=$!

wait_for_port $SUPPLIER_PORT  "reference-supplier"
wait_for_port $SIMULATOR_PORT "healthstore-simulator"

echo ""
echo "=== Seeding ==="

REG_RESPONSE=$(curl -sf -X POST \
  "http://localhost:$SIMULATOR_PORT/_simulator/registrations?cohort=$COHORT&priority=asap")
REG_ID=$(echo "$REG_RESPONSE" | jq -r .registrationId)
echo "  Seeded registration $REG_ID"

curl -sf -X POST \
  "http://localhost:$SIMULATOR_PORT/_simulator/registrations/$REG_ID/send" > /dev/null
echo "  Sent registration request to supplier"

echo ""
echo "=== Tests ==="

TOKEN=$(curl -sf -X POST \
  -d "grant_type=client_credentials&client_id=dtx-supplier&client_secret=dtx-local-secret" \
  "http://localhost:$SIMULATOR_PORT/oauth2/token" | jq -r .access_token)

REQ_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')

status=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Request-ID: $REQ_ID" \
  "http://localhost:$SIMULATOR_PORT/registrations?cohort=$COHORT&_count=50&page=1")
assert_status "GET  /registrations" 200 "$status"

status=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Request-ID: $REQ_ID" \
  "http://localhost:$SIMULATOR_PORT/registrations/$REG_ID")
assert_status "GET  /registrations/{id}" 200 "$status"

status=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Request-ID: $(uuidgen | tr '[:upper:]' '[:lower:]')" \
  -H "Content-Type: application/fhir+json" \
  -d '{"resourceType":"Task","status":"accepted","intent":"order","businessStatus":{"coding":[{"system":"https://fhir.healthstore.nhs.uk/CodeSystem/registration-business-status","code":"registered","display":"Registered"}]}}' \
  "http://localhost:$SIMULATOR_PORT/registrations/$REG_ID/tasks")
assert_status "POST /registrations/{id}/tasks" 200 "$status"

echo ""
echo "Results: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
