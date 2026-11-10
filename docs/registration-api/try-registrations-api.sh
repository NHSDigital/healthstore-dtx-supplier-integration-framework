#!/bin/sh
# Requires: sh, curl, base64, uuidgen
# Usage: ./try-registrations-api.sh <nhs-number>
# Reads CLIENT_ID, CLIENT_SECRET, TOKEN_URL and API_BASE_URL from the environment.
set -eu

if [ $# -ne 1 ]; then
  echo "Usage: $0 <nhs-number>" >&2
  exit 2
fi

if [ -z "${CLIENT_ID:-}" ] || [ -z "${CLIENT_SECRET:-}" ] || [ -z "${TOKEN_URL:-}" ] || [ -z "${API_BASE_URL:-}" ]; then
  echo "Export CLIENT_ID, CLIENT_SECRET, TOKEN_URL and API_BASE_URL first" >&2
  exit 2
fi

NHS_NUMBER=$1
SCOPE='registrations/access'
API_URL="$API_BASE_URL/registrations"
REQUEST_ID=$(uuidgen)

response_headers=$(mktemp)
trap 'rm -f "$response_headers"' EXIT

print_response_header() {
  header_name=$1
  tr -d '\r' < "$response_headers" | sed -n "s/^$header_name: //Ip"
}

encode_base64url() {
  text=$1
  printf '%s' "$text" | base64 | tr '+/' '-_' | tr -d '=\n'
}

token_response=$(curl --silent \
  --user "$CLIENT_ID:$CLIENT_SECRET" \
  --dump-header "$response_headers" \
  --data "grant_type=client_credentials&scope=$SCOPE" \
  "$TOKEN_URL")

access_token=$(printf '%s' "$token_response" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
if [ -z "$access_token" ]; then
  echo "Token request failed: $token_response" >&2
  echo "HTTP:                     $(head -1 "$response_headers" | cut -d' ' -f2)" >&2
  echo "Time:                     $(print_response_header date)" >&2
  echo "x-amz-cognito-request-id: $(print_response_header x-amz-cognito-request-id)" >&2
  exit 1
fi

# A fake ID token, accepted only until we start checking ID tokens.
id_token="$(encode_base64url '{"alg":"none","typ":"JWT"}').$(encode_base64url "{\"nhs_number\":\"$NHS_NUMBER\"}").unsigned"

report_response() {
  request_id=$1
  http_status=$(head -1 "$response_headers" | cut -d' ' -f2)
  echo "HTTP:             $http_status"
  echo "Time:             $(print_response_header date)"
  echo "X-Request-ID:     $request_id"
  echo "x-amzn-requestid: $(print_response_header x-amzn-requestid)"
  case $http_status in
    2??) ;;
    *) exit 1 ;;
  esac
}

registrations=$(curl --silent \
  --dump-header "$response_headers" \
  --header "Authorization: Bearer $access_token" \
  --header "X-Request-ID: $REQUEST_ID" \
  --header "NHSD-ID-Token: $id_token" \
  "$API_URL")
echo "$registrations"
report_response "$REQUEST_ID"

registration_id=$(printf '%s' "$registrations" \
  | grep -o '"https://fhir.dtx.national.nhs.uk/Id/registration","value":"[^"]*"' \
  | head -1 | cut -d'"' -f6)
if [ -z "$registration_id" ]; then
  echo "No registrations to acknowledge"
  exit 0
fi

# Checked but not recorded yet, so this registration is still returned next time.
TASK_REQUEST_ID=$(uuidgen)
echo
echo "Acknowledging $registration_id as registered"
curl --silent \
  --dump-header "$response_headers" \
  --write-out '\n' \
  --header "Authorization: Bearer $access_token" \
  --header "X-Request-ID: $TASK_REQUEST_ID" \
  --header 'Content-Type: application/fhir+json' \
  --data '{"resourceType":"Task","status":"accepted","intent":"order","businessStatus":{"coding":[{"system":"https://fhir.dtx.national.nhs.uk/CodeSystem/registration-business-status","code":"registered"}]}}' \
  "$API_URL/$registration_id/tasks"
report_response "$TASK_REQUEST_ID"
