# API contracts — resource paths and bodies

Resolves the two API specs for the HealthStore ↔ DTx platform boundary, mapped
from their Home-Test equivalents. Process context and lifecycle in
`process-description.md`; Home-Test evidence in
`hometest-contract-discussion-points.md`.

The two specs:

1. **DTx supplier API spec** — implemented by every DTx platform, called by
   HealthStore. One uniform contract, zero per-supplier customisation
   (decided position). Home-Test equivalent: `supplier-api-spec.yaml`.
2. **HealthStore supplier API spec** — provided by HealthStore, consumed by
   DTx platforms. Home-Test equivalent: `home-test-supplier-api.yaml`.

## Resource paths

### DTx supplier API (platforms implement, HealthStore calls)

| Path | Purpose | Home-Test analog |
|---|---|---|
| `POST /oauth/token` | Issue platform access token (client_credentials). Unauthenticated, form-encoded, non-FHIR. | `POST /oauth/token` |
| `POST /registration` | Register the patient onto the platform. Acceptance per the open acceptance-semantics decision (OQ2). | `POST /order` |
| `DELETE /registration` | Withdraw / deactivate a registration, HealthStore-initiated. Path reserved; whether it ships is OQ1. Home-Test precedent: cancellation direction-symmetric with placement. | `DELETE /order` |
| *(not carried)* | Pre-registration eligibility check. Not carried initially; revisit if platform-side quota / eligibility rules emerge. | `POST /order-eligibility` |
| *(not carried)* | Results pull. The DTx data feed is a separate concern, out of this integration's scope. | `GET /results` |

### HealthStore supplier API (HealthStore provides, platforms call)

| Path | Purpose | Home-Test analog |
|---|---|---|
| `POST /registration/status` | Signal a lifecycle transition for a registration. Activation is the first and currently only inbound transition; the shape leaves room for later lifecycle states (OQ1). | `POST /test-order/status` |
| *(not carried)* | Result submission. Data feed, out of scope here. | `POST /result` |

## Request and response bodies

### `POST /registration`

Request: FHIR R4 **ServiceRequest** with a **contained Patient** — nominally
correct as per Home-Test, carried forward.

The contained Patient departs from Home-Test in one deliberate way: Home-Test
asserts no profile; we assert the official NHS variant, which exists — **UK
Core Patient**:

- Profile: `https://fhir.hl7.org.uk/StructureDefinition/UKCore-Patient`
  (current version 2.6.1, R4, active), asserted in the contained resource's
  `meta.profile`.
- **NHS number** (required for us): the profile's identifier slice, system
  `https://fhir.nhs.uk/Id/nhs-number`, with the
  `Extension-UKCore-NHSNumberVerificationStatus` extension carrying
  verification status.
- **Gender** (required for us): `Patient.gender`, the base R4 element the
  profile retains (administrative gender, required binding to
  `administrative-gender`). Note this is administrative gender; if a clinical
  need for gender identity emerges that is a different element/extension, not
  a redefinition of this field.

Correlation carrier: the registration id. Home-Test carries it in
`ServiceRequest.id` with no `identifier` element; our leaning (positions
register) is `ServiceRequest.identifier[0]` with a declared HealthStore system
URI, minted for the purpose. To settle before spec authoring; identifier
systems to mint are listed below.

Responses:

| Code | Body | Meaning |
|---|---|---|
| `201` | — | Platform has the registration. Whether this alone means Registered, or a status callback completes acceptance, is OQ2. |
| `4xx` / `409` | `OperationOutcome` | Rejected, with a named business-rejection taxonomy the caller branches on. Our enum tbc; Home-Test precedent: `not_eligible`, `out_of_stock`, quota and fraud codes. Deterministic rejections must not be retried as transient (anti-pattern observed in Home-Test). |

### `DELETE /registration` (reserved, OQ1)

Same resource shape as placement with mutated status (Home-Test precedent:
`status: revoked`). Rejection taxonomy needs an analog of
`order_already_processed` for registrations already activated.

### `POST /registration/status`

Request: FHIR R4 **Task**.

- `identifier[0].value` = registration id (declared HealthStore system URI) —
  the load-bearing key, matching Home-Test where `Task.identifier` is the key
  actually used and `basedOn` is required but never read. Whether we keep
  `basedOn[0].reference = ServiceRequest/{id}` follows the correlation-carrier
  decision; we should not ship a required-but-unread element (decorative
  contract anti-pattern).
- Status carrier (`Task.businessStatus` vs `Task.status`) and the value set:
  tbc. First value: `activated`. The value set is where later lifecycle states
  (deactivated, withdrawn, completed) would land if OQ1 admits them.
- Candidate payload fields to resolve: timestamp of first use, and whatever
  the definition of first use (OQ4) requires the platform to attest.

Responses: success `200`/`201` tbc; errors as `OperationOutcome` (unknown
registration id, invalid transition). Repeated activation posts are expected
(reinstall, multiple devices, OQ4) and must be idempotent successes, not
errors.

### Rejections, both directions

`OperationOutcome` with `issue[].code` plus a named code in `details`, as
Home-Test. Both specs share one taxonomy definition.

## HTTP headers, both directions

As Home-Test, carried forward unchanged unless a decision says otherwise:

- `Authorization: Bearer <JWT>` — outbound token from the platform's
  `/oauth/token`; inbound token issued by HealthStore.
- `X-Correlation-ID` (UUID v4) — required on every boundary call; per
  interaction request-tracking and idempotency key (same ID + same payload =
  one side-effect; same ID + different payload = 409). Never the registration
  identity.
- `Content-Type: application/fhir+json` and `Accept: application/fhir+json`;
  success and error bodies alike are FHIR. `/oauth/token` alone is
  unauthenticated and form-encoded.

## To resolve before spec authoring

1. Correlation carrier: `ServiceRequest.id` (Home-Test) vs
   `identifier[0]` with declared system (our leaning). One choice, applied
   consistently across both specs.
2. Acceptance semantics (process OQ2) — determines the `POST /registration`
   response contract and whether a registration-accepted callback exists on
   the HealthStore API.
3. The business-rejection taxonomy enum for registrations.
4. Identifier system URIs to mint: registration id; any HealthStore-side
   patient-linkage identifiers.
5. Task status carrier and value set for `/registration/status`.
6. Whether the ServiceRequest itself asserts `UKCore-ServiceRequest` alongside
   the contained UK Core Patient, or stays base R4.
7. Whether `DELETE /registration` ships in v1 (process OQ1).
8. ServiceRequest field-level content: which enrolment fields cross the
   boundary (therapeutic descriptor, requester, commissioner) and which stay
   internal — feeds the contained-Patient minimum dataset too.
