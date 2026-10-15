# DTx Usage API: overview

The second API in this repository. The [Registrations API](../registration-api/overview.md)
puts a patient onto your platform; the Usage API is how your platform reports
what happened next. It is implemented by the HealthStore Reporting Service
(HSRS) and called by your platform; nothing calls you.

## What a usage report is

Not usage in the everyday sense: no sessions, minutes or outcomes. A report is
the minimum data set (MDS v0.1) for one patient on one product, restated
whenever something changes:

- who: NHS number and date of birth, used to pseudonymise and then deleted;
- what: the product, as an intervention code from your own catalogue, with
  its version, under your intervention code system;
- who pays: the contracting organisation's ODS code;
- how they arrived: `HealthStore` with the registration identifier you were
  given, or `Self-Referral` with none;
- account status `Open`, `Paused` or `Closed`, and whether completion applies;
- up to four dates: activation, first therapeutic activity, latest
  therapeutic activity, completion;
- the instant the statement is true, and optionally a correction flag naming
  the report it corrects.

The first report for a record is the full record with an activation date; a
record does not exist until activation. Later reports may carry only what
changed. One rule set is applied as you send (stage 1, answered at once) and
one against the record's history (stage 2, answered on request).

## The calls

| Call | Purpose |
|---|---|
| `POST /usage-reports` | One report. `202` with your Task carrying our id, or `400` with an `OperationOutcome` and nothing stored |
| `POST /usage-reports/batch` | Up to 1,000 reports as NDJSON. `200` with one entry per line |
| `GET /submissions/{id}` | The report's current status and, once judged, its reasons |
| `GET /batches/{id}` | Every stored line of a batch with its status |
| `GET /eligibility-reference` | Your products, how each is named on the wire, and the organisations each may be reported under |

Every answer about a report is FHIR R4, `application/fhir+json`: a `Task`, a
`Bundle` or an `OperationOutcome`, as the Registrations API answers. The
request is a FHIR `Bundle` holding one `Patient` and one `Task`, validated by
a strict profile: unknown elements are refused, `id`, `meta` and `fullUrl`
included, so a document built with a general FHIR library needs those
removed. The eligibility reference is reference data and plain JSON.

## Authentication

OAuth 2.0 client credentials, one client per supplier per environment, the
same pattern as the Registrations API. Interim: AWS Cognito, client
authentication `client_secret_post`, token endpoint given at onboarding.
Target: NHS England API Management. Every call carries an `X-Request-ID`, a
UUID of yours, mirrored on the answer; an optional `X-Correlation-ID` of up
to 256 printable ASCII characters is returned unchanged.

## Environments and data

| Environment | Base address | Token endpoint |
|---|---|---|
| Integration | `https://<host>/dtx-usage/v1`, host given at onboarding with your credentials | Given at onboarding (interim: AWS Cognito) |
| Production | Under NHS England API Management; to follow as a versioned change | NHS England API Management |

There is no sandpit for this API: the integration environment is the first
one you connect to. It accepts synthetic data only: NHS numbers in the
`999` test range with a valid check digit, or test patients listed for you
at onboarding. Registration identifiers are not checked against HealthStore
there; use one value per record, consistently. Identifiers are held only
until pseudonymisation completes and never enter analytics storage.

## Documents

- [Supplier guide](supplier-guide.md): from your credentials to a report
  accepted, and the test sequence that covers what your system has to handle.
  Its first section says what we need from you before onboarding.
- [How to read an answer](reading-answers.md): the three answer shapes and
  the fields to read.
- The specification: [`specification/usage-api.yaml`](../../specification/usage-api.yaml).
  The code systems it uses are in `specification/fhir/`:
  [issue codes](../../specification/fhir/CodeSystem-usage-report-issue.json),
  [status codes](../../specification/fhir/CodeSystem-usage-report-status.json).

## Status

Private beta, alongside the Registrations API. The payload is MDS v0.1 as
specified for usage reporting; the programme is still reviewing what
suppliers report, and any change comes here as a versioned update.
The orchestration event feed HSRS also consumes is internal to NHS England
and not part of this contract.
