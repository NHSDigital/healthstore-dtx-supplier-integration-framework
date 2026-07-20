# API contracts — resource paths and bodies

Defines the two API specs for the HealthStore ↔ DTx platform boundary. Process
context and lifecycle in `process-description.md`. The Home-Test supplier
integration framework is the pattern reference; it is noted where a choice
follows or departs from it, but this document defines our API.

1. **DTx supplier API spec** — implemented by every DTx platform, called by
   HealthStore. One uniform contract, zero per-supplier customisation
   (decided position).
2. **HealthStore supplier API spec** — provided by HealthStore, consumed by
   DTx platforms.

## DTx supplier API — resource paths

| Path | Purpose |
|---|---|
| `POST /oauth/token` | Issue platform access token (client_credentials). Unauthenticated, form-encoded, non-FHIR. |
| `POST /registration` | Register the patient onto the platform. Acceptance per the open acceptance-semantics decision (process OQ2). |
| `DELETE /registration` | Withdraw / deactivate a registration, HealthStore-initiated. Path reserved; whether it ships is process OQ1. |

Not included: a pre-registration eligibility check (revisit if platform-side
eligibility or quota rules emerge) and any results pull.

The DTx data feed is
a separate concern outside this integration.

## HealthStore supplier API — resource paths

| Path | Purpose |
|---|---|
| `POST /registration/status` | Signal a lifecycle transition for a registration. Activation is the first and currently only inbound transition; the shape leaves room for later lifecycle states (process OQ1). |

Not included: result / data-feed submission, as above.

## `POST /registration` — request body

One FHIR R4 **ServiceRequest** with a **contained Patient**. A single
resource, not a Bundle: everything the prior Bundle model carried is either an
element of the ServiceRequest, an element of the contained Patient, or a
logical identifier reference. Supporting info is not carried (decision: not a
thing).

### ServiceRequest elements

| Element | Definition |
|---|---|
| `identifier[]` | The upstream referral identifier, with its system declared (UBRN / rx-id). Gains a second slice for the registration id if the correlation-carrier decision lands there (to-resolve 1). |
| `status` | `active`. |
| `intent` | `order`. |
| `subject` | Reference to the contained Patient (`#<id>`). |
| `code` | The DTx care pathway (CodeableConcept). Code systems tbc — SNOMED / dm+d per the enrolment's therapeutic descriptor. |
| `requester` | The enrolling organisation as a logical identifier reference (ODS code, system `https://fhir.nhs.uk/Id/ods-organization-code`). Whether the practitioner (SDS user id) is carried alongside is to-resolve 8. |
| `performer[0]` | The target DTx platform / service. |
| `contained[0]` | The Patient, below. |

All organisation and practitioner references are **logical identifier
references** (`Reference.identifier` with a declared system), never shipped
resources and never reference strings that look resolvable but are not. This
keeps the message a single resource with honest reference semantics.
(Home-Test ships `Organization/{id}`-style strings that resolve to nothing in
the message; we make the same logical intent explicit.)

### Contained Patient

Profiled as **UK Core Patient** — the official NHS variant — asserted in the
contained resource's `meta.profile`:
`https://fhir.hl7.org.uk/StructureDefinition/UKCore-Patient` (v2.6.1, R4).

| Element | Definition |
|---|---|
| `identifier` | **NHS number, required.** The profile's identifier slice, system `https://fhir.nhs.uk/Id/nhs-number`, with the `Extension-UKCore-NHSNumberVerificationStatus` extension. |
| `name` | Patient name. |
| `birthDate` | Date of birth. |
| `gender` | **Required.** `Patient.gender`, administrative gender (required binding to `administrative-gender`). If a clinical need for gender identity emerges that is a different element/extension, not a redefinition of this field. |
| `telecom` | Patient contact points — flagged: this is out-of-app contact data reaching the platform before activation, which is exactly process OQ3's concern. Whether telecom ships full, reduced, or withheld is part of settling that question. |
| `address` | Patient address. |
| `generalPractitioner` | Registered GP as a logical identifier reference (ODS code). No second contained resource. |

Reference note: Home-Test's equivalent contained Patient carries name, birth
date, telecom and address but no identifiers, gender or GP, and asserts no
profile. Our NHS number, gender and registered GP therefore have no reference
precedent; the UK Core profile supplies their shape, which is a further reason
to assert it.

### Responses

| Code | Body | Meaning |
|---|---|---|
| `201` | — | Platform has the registration. Whether this alone means Registered, or a status callback completes acceptance, is process OQ2. |
| `4xx` / `409` | `OperationOutcome` | Rejected, with a named business-rejection code the caller branches on (enum to-resolve 3). Deterministic rejections must not be retried as transient. |

## `DELETE /registration` (reserved, process OQ1)

Same resource shape as placement with mutated status. The rejection taxonomy
needs a code for registrations already activated. (Home-Test precedent:
cancellation direction-symmetric with placement, `status: revoked`.)

## `POST /registration/status` — request body

One FHIR R4 **Task**.

| Element | Definition |
|---|---|
| `identifier[0]` | The registration id, with a declared HealthStore system URI — the load-bearing correlation key. |
| status carrier | `Task.businessStatus` vs `Task.status` and the value set: to-resolve 5. First value: `activated`; later lifecycle states (deactivated, withdrawn, completed) land here if process OQ1 admits them. |
| payload fields | Candidates to resolve: timestamp of first use, and whatever the definition of first use (process OQ4) requires the platform to attest. |

No element ships required-but-unread: if the correlation-carrier decision
makes `basedOn` redundant, it is omitted rather than demanded decoratively.

Responses: success `200`/`201` tbc; errors as `OperationOutcome` (unknown
registration id, invalid transition). Repeated activation posts are expected
(reinstall, multiple devices — process OQ4) and are idempotent successes, not
errors.

## Rejections, both directions

`OperationOutcome` with `issue[].code` plus a named code in `details`. Both
specs share one taxonomy definition.

## HTTP headers, both directions

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

1. Correlation carrier for the registration id: `ServiceRequest.id` vs
   `identifier[]` with a declared system (our leaning). One choice, applied
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
8. Residual field content: code systems for the care-pathway `code`; telecom
   full / reduced / withheld pre-activation (with process OQ3); whether the
   requester carries the practitioner as well as the organisation.
