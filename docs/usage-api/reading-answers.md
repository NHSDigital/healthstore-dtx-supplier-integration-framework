# How to read an answer from the DTx Usage API

*Sent with the [supplier guide](supplier-guide.md); the specification in this repository, `specification/usage-api/usage-api.yaml`, has every field and code.*

Every answer is FHIR JSON, as the HealthStore Registration API's are. There
are three shapes. This page shows each one as it comes back and names the
fields your code should read; the full field and code reference is the API
docs page.

Every answer also carries back the `X-Request-ID` you sent, so a log line on
your side and one on ours can be matched.

## 1. A refusal: `OperationOutcome`

Any status from 400 to 503, on any route, is an `OperationOutcome`. Read
`issue[]`: one entry per thing wrong, each with our code under
`details.coding[0].code`, a sentence in `diagnostics`, and, where it applies,
the field in `expression[0]`.

```json
{
  "resourceType": "OperationOutcome",
  "issue": [
    {
      "severity": "error",
      "code": "value",
      "details": { "coding": [{ "system": "https://fhir.dtx.national.nhs.uk/CodeSystem/usage-report-issue", "code": "INVALID_NHS_NUMBER" }] },
      "diagnostics": "NHS number must be 10 digits with a valid mod-11 check digit",
      "expression": ["entry.Patient.identifier"]
    }
  ]
}
```

What to read: `details.coding[0].code` is the code to act on; `code` is
FHIR's classification of it and is for display. A 400 lists every problem it
found, so fix them all before resending. `PRODUCT_NOT_PERMITTED` comes alone
and says nothing else about the report. A 401 or 403 from the gateway has the
same shape with `TOKEN_INVALID`, `TOKEN_EXPIRED` or `NO_ACCESS`; a 429,
`TOO_MANY_REQUESTS`.

## 2. One report accepted: `Task`

`POST /usage-reports` answers 202 with the Task you sent, as we hold it, plus
what we added. `GET /submissions/{id}` answers 200 with the same Task later.

```json
{
  "resourceType": "Task",
  "id": "6f1d1b2e-6c9e-4d0a-9a3f-2f7c1a0b8e55",
  "identifier": [{ "system": "https://fhir.dtx.national.nhs.uk/Id/usage-report-submission", "value": "6f1d1b2e-6c9e-4d0a-9a3f-2f7c1a0b8e55" }],
  "status": "received",
  "statusReason": { "coding": [{ "system": "https://fhir.dtx.national.nhs.uk/CodeSystem/usage-report-status", "code": "received" }] },
  "intent": "order",
  "authoredOn": "2026-10-03T10:15:02.114Z",
  "lastModified": "2026-10-03T09:58:00Z",
  "requester": { "identifier": { "system": "https://fhir.nhs.uk/Id/ods-organization-code", "value": "QHS1" } },
  "basedOn": [{ "identifier": { "system": "https://fhir.dtx.national.nhs.uk/Id/registration", "value": "..." } }],
  "businessStatus": { "text": "Open" },
  "input": [ "... the inputs you sent ..." ],
  "output": [ "... the output dates you sent ..." ]
}
```

What to read:

| Field | Meaning |
|---|---|
| `id` | Our submission id. Keep it: it is what you poll and what you quote to us. |
| `statusReason.coding[0].code` | Where the report stands, in our words: `received`, `pending_merge`, `parked`, `accepted`, `rejected`, `duplicate`, `superseded`. This is the field to branch on. |
| `status` | FHIR's nearest reading of the same thing (`received`, `on-hold`, `accepted`, `rejected`, `completed`). For display only. |
| `authoredOn` | When we received it. |
| `lastModified`, `input`, `output`, `requester`, `basedOn`, `businessStatus` | What you sent, as recorded. The Patient is never returned. |

A 202 always says `received`: stage 2 has not run yet. Poll
`GET /submissions/{id}` for the outcome; it is usually there within seconds
and always within a minute or two under load.

**When a report is rejected or a duplicate**, the Task carries a contained
`OperationOutcome` with the reasons, referenced from `output`:

```json
{
  "resourceType": "Task",
  "id": "...",
  "status": "rejected",
  "statusReason": { "coding": [{ "system": "https://fhir.dtx.national.nhs.uk/CodeSystem/usage-report-status", "code": "rejected" }], "text": "merged record would violate activation_datetime <= first_therapeutic_activity_datetime" },
  "contained": [
    {
      "resourceType": "OperationOutcome",
      "id": "outcome",
      "issue": [
        { "severity": "error", "code": "invariant",
          "details": { "coding": [{ "system": "https://fhir.dtx.national.nhs.uk/CodeSystem/usage-report-issue", "code": "ORDERING_VIOLATION" }] },
          "diagnostics": "merged record would violate activation_datetime <= first_therapeutic_activity_datetime" }
      ]
    }
  ],
  "output": [ "... your dates ...", { "type": { "text": "outcome" }, "valueReference": { "reference": "#outcome" } } ]
}
```

What to read: `contained[0].issue[].details.coding[0].code`, the same way as
a refusal. `statusReason.text` repeats the first reason for a human. A
`duplicate` carries `DUPLICATE_SUBMISSION` and changed nothing; a `rejected`
report changed nothing either, and the record stands as it was.

## 3. A batch: `Bundle`

`POST /usage-reports/batch` answers 200 with a `batch-response` Bundle, one
entry per line, in the order you sent them. A line that passed stage 1 has a
`202 Accepted` response and its Task; a line that did not has a
`400 Bad Request` response and an `OperationOutcome`.

```json
{
  "resourceType": "Bundle",
  "id": "a2c4...",
  "identifier": { "system": "https://fhir.dtx.national.nhs.uk/Id/usage-report-batch", "value": "a2c4..." },
  "type": "batch-response",
  "entry": [
    { "response": { "status": "202 Accepted" }, "resource": { "resourceType": "Task", "id": "...", "...": "..." } },
    { "response": { "status": "400 Bad Request", "outcome": { "resourceType": "OperationOutcome", "issue": [ "..." ] } } }
  ]
}
```

What to read: `entry[i].response.status` for the line's fate at stage 1, then
`entry[i].resource.id` for the submission id or
`entry[i].response.outcome.issue[]` for why it was refused. Keep `id`: it is
the batch id. `GET /batches/{id}` later answers a `collection` Bundle whose
`entry[].resource` are the Tasks as they stand, each read as in section 2,
with `total` the number of lines we hold for you.

## Reading them in code

Branch on three things and nothing else: the HTTP status; for a Task,
`statusReason.coding[0].code`; for a reason, `details.coding[0].code`. The
system URLs are fixed and are not fetched. Ignore fields you do not
recognise: we may add some, and will not remove or rename any within this
version of the API.
