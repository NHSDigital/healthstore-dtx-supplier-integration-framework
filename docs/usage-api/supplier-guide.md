# DTx Usage API: supplier guide

For the supplier's developers. It takes you from the values you were sent to a
usage report accepted by the integration environment, then through a test
sequence that covers the cases your system has to handle.

**You are done when** every step of the test sequence gives its expected
result from your own system.

Synthetic data only. The integration environment is not for patient data.

The commands use `curl` and `jq` in a shell. They are there to show each call
exactly; your system makes the same calls.

## What we need from you

Before we can set you up, send us three things. They are yours, not ours,
and nothing in them is a commitment beyond the integration environment.

1. **Your intervention codes.** The code your test environment puts on a
   usage report for each of your products, from your own catalogue, with the
   version it will send. We check the code system's shape only and read the
   code as yours, so the name in the system URL is your choice.
2. **The contracting organisation codes in your test data.** The ODS codes of
   the commissioners your test patients are reported under. The pairs of
   product and organisation are what a report is checked against.
3. **How you build the document.** By hand, or with a FHIR library. A library
   adds fields such as `id`, `meta` and `fullUrl`, which this API refuses;
   knowing which you use lets us say so before your first call fails.

And, only if your test patients carry NHS numbers that do not start `999`:
the list of those numbers, which we load as your test patients. Synthetic
numbers only; the integration environment is not for patient data.

Until you send the first two, we set you up with placeholders and tell you so
on your page; your tests then run against the placeholders, and we swap in
your codes when they arrive, with no change on your side but the two values.

## Before you start

You were sent a page with seven values. Set them in your shell as the page
shows, or:

```bash
export API_BASE='<API base address, ends /dtx-usage/v1>'
export TOKEN_URL='<token endpoint>'
export CLIENT_ID='<client id>'
export CLIENT_SECRET='<client secret>'
export INTERVENTION_SYSTEM='https://fhir.dtx.national.nhs.uk/CodeSystem/intervention-<a name of yours>'
export INTERVENTION_CODE='<your code for the product>'
export COMMISSIONER='<a contracting organisation code for that product>'
```

Every call also carries an `X-Request-ID` header: a UUID of your own for
that call, the same value if you retry it. It comes back on the answer and is
what you quote to us. The helpers below set one on each call.

Keep the secret out of source control. In your system, make the API base
address and the token endpoint configuration: both change when the API moves
behind NHS England API Management. Paths, bodies and codes do not.

The specification in this repository, `specification/usage-api/usage-api.yaml`, and the API docs page you were sent have the full field and code reference, and
[`reading-answers.md`](reading-answers.md), sent with this guide, shows each answer shape and the fields to read.

## 1. Get a token

You exchange the client id and secret for a token that lasts an hour, and send
the token on every call.

```bash
export ACCESS_TOKEN=$(curl -s -X POST "$TOKEN_URL" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=$CLIENT_ID&client_secret=$CLIENT_SECRET" \
  | jq -r .access_token)
echo "${#ACCESS_TOKEN} characters"
```

Expected: a number over 500. `4 characters` means the request was refused
(the token is the word `null`); check the four values above.

Keep the token for the hour. Do not request one per call.

## 2. Check the token works

This call returns the products you may report for, how each is named on the
wire, and the contracting organisations each may be reported under.

```bash
curl -s "$API_BASE/eligibility-reference" -H "Authorization: Bearer $ACCESS_TOKEN" -H "X-Request-ID: $(uuidgen)" \
  | jq -r '. as $r | ["CODE", "COMMISSIONER"], ($r.pairs[] | select(.valid_to == null) | .product_id as $p | ($r.products[] | select(.product_id == $p)) as $prod | [$prod.intervention_code, .commissioner_ods_code]) | @tsv' \
  | column -t
```

Expected: a heading, then one line per product and contracting organisation
you may report for. `INTERVENTION_CODE` and `COMMISSIONER` must be one of
these lines. `INTERVENTION_SYSTEM` is your intervention code system,
`https://fhir.dtx.national.nhs.uk/CodeSystem/intervention-<name>`, with a
name of your choosing, your organisation's in lower case for instance. We
check its shape and nothing else: the code is yours, and we read it as yours.

## 3. Send a report

A report is a FHIR Bundle holding one Patient and one Task. This builds a
first report for a new record:

```bash
export NHS_NUMBER=9990000018
export REQUEST_ID="req-$(date +%s)"
export NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)

cat > report.json <<EOF
{
  "resourceType": "Bundle",
  "type": "collection",
  "entry": [
    {
      "resource": {
        "resourceType": "Patient",
        "identifier": [{ "system": "https://fhir.nhs.uk/Id/nhs-number", "value": "$NHS_NUMBER" }],
        "birthDate": "1958-03-14"
      }
    },
    {
      "resource": {
        "resourceType": "Task",
        "status": "in-progress",
        "intent": "order",
        "requester": { "identifier": { "system": "https://fhir.nhs.uk/Id/ods-organization-code", "value": "$COMMISSIONER" } },
        "basedOn": [{ "identifier": { "system": "https://fhir.dtx.national.nhs.uk/Id/registration", "value": "$REQUEST_ID" } }],
        "businessStatus": { "text": "Open" },
        "lastModified": "$NOW",
        "input": [
          { "type": { "text": "intervention" }, "valueCodeableConcept": { "coding": [{ "system": "$INTERVENTION_SYSTEM", "code": "$INTERVENTION_CODE", "version": "2.3.1" }] } },
          { "type": { "text": "registration_route" }, "valueString": "HealthStore" },
          { "type": { "text": "completion_applicable" }, "valueBoolean": true }
        ],
        "output": [
          { "type": { "text": "activation_datetime" }, "valueDateTime": "2026-08-25T09:30:00Z" }
        ]
      }
    }
  ]
}
EOF
```

Create a helper named `send`. It is used in the rest of this guide. `send
<path> <file>` posts the file with your token and a fresh `X-Request-ID`,
prints the status code and a summary of the answer, and keeps the id from
the answer in `SUBMISSION_ID` (a Task) or `BATCH_ID` (a Bundle).

```bash
send() {
  local answer body
  answer=$(curl -s -w '\nHTTP %{http_code}' -X POST "$API_BASE/$1" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H "X-Request-ID: $(uuidgen)" \
    -H "Content-Type: application/fhir+json" \
    --data-binary @"$2")
  body=$(echo "$answer" | head -1)
  echo "$answer" | tail -1
  echo "$body" | jq -c 'if .resourceType == "Task" then {id, status}
    elif .resourceType == "Bundle" then {id, lines: [.entry[].response.status]}
    else {refused: [.issue[].details.coding[0].code]} end'
  export SUBMISSION_ID=$(echo "$body" | jq -r 'select(.resourceType == "Task") | .id // empty')
  export BATCH_ID=$(echo "$body" | jq -r 'select(.resourceType == "Bundle") | .id // empty')
}
```

Send the report:

```bash
send usage-reports report.json
```

Expected: `HTTP 202` and `{"id": "...", "status": "received"}`: the report
passed the first checks and is stored, and the answer is your Task with our
id on it. A `400` means nothing was stored; the answer is an
`OperationOutcome` listing each problem, and the helper prints its codes.

## 4. Read the result

The outcome is not in the `202`. Nothing is pushed to you; ask for it.

Create a helper named `status`, also used in the rest of this guide. `status
<submissionId>` prints the outcome of one report: the status from the Task's
`statusReason`, and the reason codes from the outcome it contains. A report
is stored at once and usually judged within seconds; the helper asks every
few seconds until there is a verdict, for up to two minutes.

```bash
status() {
  local answer
  for _ in $(seq 1 40); do
    answer=$(curl -s "$API_BASE/submissions/$1" -H "Authorization: Bearer $ACCESS_TOKEN" -H "X-Request-ID: $(uuidgen)" \
      | jq -c 'if .resourceType == "Task" then {status: .statusReason.coding[0].code, reasons: [.contained[]?.issue[].details.coding[0].code]} else . end')
    case "$answer" in *'"received"'*|*'OperationOutcome'*) sleep 3 ;; *) break ;; esac
  done
  echo "$answer"
}
```

Read the result:

```bash
status "$SUBMISSION_ID"
```

Expected: `{"status":"accepted","reasons":[]}`. If it prints `received`
after two minutes, the judging process is behind: tell us.

| `stage2Status` | Meaning | Do |
|---|---|---|
| `received` | Stored, not yet judged | Ask again; the helper does |
| `accepted` | Merged into the record | Nothing |
| `duplicate` | Identical to a report already held | Nothing |
| `pending_merge` | Self-referral, waiting for the patient to be matched | Ask again later. In the integration environment, usually within two hours |
| `rejected` | Not merged. `reasons` says why | Fix and send again |
| `superseded` | Replaced by a later correction | Nothing |

## Test sequence

Steps 1 to 4 above are the first four. Continue in the same shell. A `202`
only says the report was stored, so each step that gets one reads the outcome
as well; `status` waits for the verdict.

**5. The same report again, unchanged.** Your system will resend after a
timeout; that must be harmless.

```bash
send usage-reports report.json
status "$SUBMISSION_ID"
```

Expected: `HTTP 202`, then status `duplicate`.

**6. A later report for the same record.** Same patient and request id, a
later `lastModified`, and the field that changed.

```bash
jq --arg now "$(date -u +%Y-%m-%dT%H:%M:%SZ)" '
  .entry[1].resource.lastModified = $now
  | .entry[1].resource.output = [{ type: { text: "latest_therapeutic_activity_datetime" }, valueDateTime: "2026-09-01T08:00:00Z" }]
' report.json > later.json
send usage-reports later.json
status "$SUBMISSION_ID"
```

Expected: `HTTP 202`, then status `accepted`.

**7. A partial report for a record never reported in full.** The first report
for a record must be the full record with `activation_datetime`.

```bash
jq --arg id "req-new-$(date +%s)" '
  .entry[0].resource.identifier[0].value = "9990000026"
  | .entry[1].resource.basedOn[0].identifier.value = $id
  | .entry[1].resource.output = []
' report.json > partial.json
send usage-reports partial.json
status "$SUBMISSION_ID"
```

Expected: `HTTP 202`, then status `rejected` with reason
`INCOMPLETE_FIRST_SUBMISSION`.

**8. An NHS number that is not valid.** The last digit of an NHS number is a
check digit. This one is wrong.

```bash
jq '.entry[0].resource.identifier[0].value = "9990000019"' report.json > bad-number.json
send usage-reports bad-number.json
```

Expected: `HTTP 400` with `INVALID_NHS_NUMBER`. A valid number that does not
start `999` gets `NHS_NUMBER_OUT_OF_TEST_RANGE`.

**9. A product that is not yours.**

```bash
jq '(.entry[1].resource.input[] | select(.type.text == "intervention") | .valueCodeableConcept.coding[0].code) = "not-mine"' report.json > not-mine.json
send usage-reports not-mine.json
```

Expected: `HTTP 400` with `PRODUCT_NOT_PERMITTED` and nothing else. A
mistyped code of your own gets the same answer. The code system is not what
decides: a code is read as yours whatever name follows `intervention-`.

**10. No token.**

```bash
curl -s -o /dev/null -w 'HTTP %{http_code}\n' "$API_BASE/eligibility-reference"
```

Expected: `HTTP 401`.

**11. Several at once.** One report per line. At most 1,000 lines or 4 MB per
call; send larger extracts as several calls.

```bash
jq -c . later.json bad-number.json > reports.ndjson
send usage-reports/batch reports.ndjson
```

Expected: `HTTP 200` and `lines: ["202 Accepted", "400 Bad Request"]`. The
answer is a Bundle with one entry per line: line 1 carries its Task with an
id, line 2 carries an `OperationOutcome` with the reasons. A bad line does
not stop the others.

```bash
sleep 15; curl -s "$API_BASE/batches/$BATCH_ID" -H "Authorization: Bearer $ACCESS_TOKEN" -H "X-Request-ID: $(uuidgen)" \
  | jq -c '[.entry[].resource | {id, status: .statusReason.coding[0].code}]'
```

Expected: one entry, the stored line, as `duplicate`: it repeats step 6. A
`received` status means a line is not judged yet: wait and ask again.

## Rules that catch people

| Rule | If broken |
|---|---|
| The first report for a record is the full record, with `activation_datetime`. Do not report patients who have registered but not activated | `rejected` `INCOMPLETE_FIRST_SUBMISSION` |
| Later reports may leave out the `output` dates that have not changed. Everything else in the example is sent every time, the `intervention` input included | `400` `MALFORMED_BUNDLE` |
| Route `HealthStore` has a registration id. Route `Self-Referral` has none | `400` `ROUTE_CONSISTENCY` |
| Every later report for a record carries the same registration id as the first | A new record is started |
| Every call carries an `X-Request-ID` that is a UUID | `400` |
| NHS numbers in the integration environment start `999` and have a valid check digit, or are test patients we have listed for you | `400` `NHS_NUMBER_OUT_OF_TEST_RANGE` or `INVALID_NHS_NUMBER` |
| Dates and times carry an offset (`Z` or `+01:00`) | `400` `MALFORMED_BUNDLE` |
| Dates exist (no 30 February) and fall between 1800 and 2100 | `400` `MALFORMED_BUNDLE` |
| Ids, versions and codes are at most 200 characters, with no control characters | `400` `MALFORMED_BUNDLE` |
| `lastModified` is not in the future | `400` `FUTURE_RECORD_TIMESTAMP` |
| No fields beyond those in the docs page. This includes `fullUrl`, `id` and `meta` | `400` `MALFORMED_BUNDLE` |
| Activation, first activity and completion times do not change once set. To fix one, send a correction | `rejected` `IMMUTABLE_FIELD_CONFLICT` |
| Your own products only | `400` `PRODUCT_NOT_PERMITTED` |

## When a call fails

| You see | Cause |
|---|---|
| No token from the token request | Wrong client id, secret or token endpoint |
| `401` on the API | Token expired, missing or malformed. Get a new token and retry once |
| `403` on the API | `NO_ACCESS`: your credentials may not call this route. `INVALID_REQUEST`: wrong path or method |
| `404` on a status | The id came from a `400`, or from other credentials |
| `429` | Too many calls. Wait and retry |
| `400` | The `OperationOutcome` says which rule and which field: `issue[].details.coding[0].code` and `issue[].expression` |

Every refusal is an `OperationOutcome`, from the gateway and from the
service alike. When you raise a problem with us, quote the `X-Request-ID`
you sent; it comes back on every answer, so you can match the two.

For `401`, `403` and `429`, act on the status code, not on the body's text.

## Limits of the integration environment

- One rate limit is shared by all suppliers. Agree load test times with us first.
- Registration ids are not checked against real HealthStore registrations. Any value is accepted.
- The intervention code and contracting organisation code on your page are placeholders until you send us yours.
- You can read the status of your own submissions only.
