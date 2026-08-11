# FHIR resources and fields

## Outbound registration request

A `Task`, sent by HealthStore to the supplier platform.

| Element | Value | Notes |
|---|---|---|
| `resourceType` | `Task` | |
| `meta.profile` | Omitted | Conforms to base R4 `Task` |
| `identifier` | Issued by HealthStore | Identifies the registration request, not the registration |
| `authoredOn` | When the registration request was created | Stable across redeliveries |
| `status` | `requested` | "The task is ready to be acted upon and action is sought." |
| `intent` | `order` | |
| `code` | Process specific service request, or Process available service requests | Scope |
| `priority` | Matches `ServiceRequest.priority` | Constrained from `request-priority` to `routine` and `asap`: `asap` for a specific request, `routine` for available requests |
| `focus` | `Reference(ServiceRequest)` | Required for a specific request, absent for an available-requests one |
| `groupIdentifier` | Cohort identifier | Required for an available-requests one, absent otherwise |
| `for` | Omitted | |
| `requester` | Omitted | |
| `owner` | Omitted | |

`code` states the scope. `priority` says whether the patient was present: `asap` where the
registration is made face to face with the practitioner and patient present,
`routine` for an invited cohort. The registration request and the registration
it concerns have the same value.

`code`, `focus` and `groupIdentifier` must agree:

| `code` | `focus` | `groupIdentifier` |
|---|---|---|
| `process-specific-service-request` | required | must be absent |
| `process-available-service-requests` | must be absent | required |

Where `code`, `focus` and `groupIdentifier` disagree, the request fails the
schema and is rejected with a 400.

A registration request may arrive more than once, with the same `identifier` each
time. HealthStore chooses its own retry schedule; the limits on it are set out in
the requirements.

### Required by this contract

Beyond base FHIR, which mandates only `status` and `intent`:

- `identifier`
- `code`
- `priority`
- `authoredOn`
- `focus`, when `code` is `process-specific-service-request`
- `groupIdentifier`, when `code` is `process-available-service-requests`

### Response

`200`, returning the same Task with its status changed.

| Element | Value |
|---|---|
| `status` | `accepted` |
| `lastModified` | When the platform accepted |
| `identifier` | Echoed unchanged |

`accepted` is "The potential performer has agreed to execute the task but has
not yet started work." Nothing later in the flow refers to the registration
request again.

## Registration

A registration is a `ServiceRequest`, retrieved whole. It requires no further
request to resolve.

Where each reference sits:

| Reference | Placement |
|---|---|
| `subject` → Patient | Contained and required. `ServiceRequest.contained` includes at least the Patient, and `subject.reference` is `#<id>` |
| `requester` → Organization | ODS code in `requester.identifier`, name in `requester.display`. No resource is sent. The clinician proposal replaces this with a contained `PractitionerRole` |
| `performer` → provider or service | ODS code in `performer.identifier`, name in `performer.display`. No resource is sent |
| `reasonReference` | Not included |
| `supportingInfo` | Not included |

No reference is given as a URL. `meta.profile` is omitted throughout; conformance
is stated in this document rather than asserted on the instance.

Patients are validated against PDS upstream of registration.

### Patient

Profiled against `UKCore-Patient` 2.5.0, package `fhir.r4.ukcore.stu2` 2.1.0.

| Field | FHIR R4 element | Format | Card |
|---|---|---|---|
| `first_name` | `Patient.name.where(use='official').given[0]` | string | 1..1 |
| `last_name` | `Patient.name.where(use='official').family` | string | 1..1 |
| `date_of_birth` | `Patient.birthDate` | `YYYY-MM-DD` | 1..1 |
| `nhs_number` | `Patient.identifier:nhsNumber` | 10 digits, mod-11; the schema checks format only | 1..1 |
| `nhs_number_verification_status` | `Patient.identifier:nhsNumber.extension:nhsNumberVerificationStatus` | Fixed `01`, "Number present and verified", system `https://fhir.hl7.org.uk/CodeSystem/UKCore-NHSNumberVerificationStatusEngland` | 1..1 |
| `email` | `Patient.telecom` where `system=email` | valid email, `use=home` | 1..1 |
| `phone` | `Patient.telecom` where `system=phone` | E.164 | 1..1 |
| `gender` | `Patient.gender` | administrative-gender | 1..1 |
| `country` | `Patient.address[0].country` | ISO 3166-1 alpha-2, fixed `GB` | 0..1 |
| registered GP | `Patient.generalPractitioner.identifier` | system `https://fhir.nhs.uk/Id/ods-organization-code` | 1..1 |

`UKCore-Patient` slices `Patient.identifier`. The `nhsNumber` slice is `0..1`,
fixes `system` to `https://fhir.nhs.uk/Id/nhs-number`, requires `value`, and
permits an `nhsNumberVerificationStatus` extension at `0..1`. This contract
requires that extension and fixes it to `01`.

NHS HealthStore MUST verify the patient against PDS before registration, and
MUST NOT attempt to register a patient it could not verify.

`telecom.use` permits only `home`, `work`, `temp`, `old`, `mobile` and
`billing`.

Cardinality above is set within our own contract rather than by UK Core.

### ServiceRequest

The cardinality column gives base FHIR R4. This contract requires `identifier`,
`status`, `intent`, `subject`, a contained Patient, and `performer`, which decides
which platform may retrieve the registration. The rest are optional in the
schema.

| Field | FHIR R4 element | Base card | Rule |
|---|---|---|---|
| `registration_id` | `ServiceRequest.identifier` | 0..* | The registration identifier, system `https://fhir.healthstore.nhs.uk/Id/registration`. Gives idempotency |
| `status` | `ServiceRequest.status` | 1..1 | `active` when submitted. Bound to `request-status` |
| `intent` | `ServiceRequest.intent` | 1..1 | `order`. Bound to `request-intent` |
| `patient` | `ServiceRequest.subject` | 1..1 | `Reference(Patient)`. Base also permits Group, Location, Device |
| `care_path_code` | `ServiceRequest.code` | 0..1 | Coded digital therapeutic programme or pathway. FHIR binds this element for illustration only, so the value set is ours to define |
| `authored_on` | `ServiceRequest.authoredOn` | 0..1 | "When the request transitioned to being actionable." The practitioner's act, not HealthStore creating the registration |
| `requester` | `ServiceRequest.requester` | 0..1 | Base permits Practitioner, PractitionerRole, Organization, Patient, RelatedPerson and Device. This contract uses Organization today |
| `performer` | `ServiceRequest.performer` | 0..* | Intended digital therapeutic provider or service |
| `reason` | `ServiceRequest.reasonCode` | 0..* | Clinical indication. FHIR binds this element for illustration only, so the value set is ours to define |
| `priority` | `ServiceRequest.priority` | 0..1 | Constrained from `request-priority` to `routine` and `asap`, the same values as `Task.priority`. `asap` where made face to face with the practitioner attending, `routine` for an invited cohort. The registration request has the same value |

Demographics sit on the Patient, reached through `ServiceRequest.subject`.

Nothing records the moment HealthStore created the registration, which stays
internal. `ServiceRequest.authoredOn` records the practitioner's act, and
`Task.authoredOn` the sending of the registration request.

Every field is needed for the registration to work, and none is present only for
reporting. The cardinality column is therefore the only distinction that matters:
what is required is required to register.

The commissioning body, proposed as a contained `Coverage`, is reported on as
well, but it is needed too, since a platform checks it to reject a registration
for a region it is not licensed to serve.

### Organisation identity

| Concern | Home | State |
|---|---|---|
| The registered GP practice | `Patient.generalPractitioner` | Decided |
| The digital therapeutic provider | `ServiceRequest.performer` | Decided |
| The clinician responsible for care | `ServiceRequest.requester` → contained `PractitionerRole` | Proposed |
| The practitioner placing the enrolment | Not in the registration, internal to HealthStore | Proposed |
| The commissioning body | `ServiceRequest.insurance` → contained `Coverage`, named in `payor` | Proposed |

The proposals are in `open-questions-and-decisions.md`.

UK Core publishes four extensions on ServiceRequest. None is used here.

| Extension | Value |
|---|---|
| `Extension-UKCore-SourceOfServiceRequest` | `CodeableConcept`, preferred binding to a SNOMED value set. The type of source, not its identity |
| `Extension-UKCore-AdditionalContact` | `Reference(Organization \| Practitioner \| PractitionerRole)`. Who to contact about questions arising |
| `Extension-UKCore-Coverage` | `CodeableConcept`, extensible binding to `UKCore-FundingCategory` |
| `Extension-UKCore-PriorityReason` | On `ServiceRequest.priority`. `CodeableConcept`, preferred binding to `UKCore-ServiceRequestReasonCode`. Would give why a registration is `asap` |

## Lifecycle Task

A `Task`, submitted by the platform to HealthStore against a registration.

| Element | Value | Notes |
|---|---|---|
| `resourceType` | `Task` | |
| `status` | A `task-status` value | The Task's own workflow status, not the registration's state |
| `intent` | A `task-intent` value | |
| `businessStatus` | `registered`, `rejected`, `activated` or `deactivated` | The registration's state |
| `statusReason` | A rejection reason | Set where `businessStatus` is `rejected` |

`businessStatus` gives the registration's state. `Task.status` describes the Task.
The response to a lifecycle Task, and what else the Task should contain, are open.

The rejection reasons defined so far are `not-licensed` and `duplicate`. The full
set is open.

## System URIs

A system URI appears only on elements that are codes or identifiers.

| Resource | Element | FHIR type | System URI |
|---|---|---|---|
| Registration request Task | `status`, `intent`, `priority` | `code` | None |
| Registration request Task | `code` | `CodeableConcept` | `https://fhir.healthstore.nhs.uk/CodeSystem/task-code` |
| Registration request Task | `identifier` | `Identifier` | `https://fhir.healthstore.nhs.uk/Id/registration-request` |
| Registration request Task | `groupIdentifier` | `Identifier` | `https://fhir.healthstore.nhs.uk/Id/cohort` |
| Registration request Task | `focus` | `Reference` | `https://fhir.healthstore.nhs.uk/Id/registration`, if referenced by identifier rather than by URL |
| Lifecycle Task | `businessStatus` | `CodeableConcept` | `https://fhir.healthstore.nhs.uk/CodeSystem/registration-business-status` |
| Lifecycle Task | `statusReason` | `CodeableConcept` | `https://fhir.healthstore.nhs.uk/CodeSystem/rejection-reason` |
| ServiceRequest | `identifier` | `Identifier` | `https://fhir.healthstore.nhs.uk/Id/registration` |
| ServiceRequest | `code` | `CodeableConcept` | Ours to define, see the care path code open questions |

Each `system` value is fixed.

These URIs are provisional and assume HealthStore takes
its own `fhir.healthstore.nhs.uk` subdomain, and that what the supplier sees is
a registration rather than an enrolment.

Both values of `code` come from
`https://fhir.healthstore.nhs.uk/CodeSystem/task-code`:

| `code` | `display` | Used for |
|---|---|---|
| `process-specific-service-request` | Process specific service request | A single registration |
| `process-available-service-requests` | Process available service requests | A cohort |

### Identifier values

| System | Identifies | Issued by | Format |
|---|---|---|---|
| `registration` | One registration | HealthStore, when the registration is created. Stable for its life | UUID |
| `registration-request` | One registration request | HealthStore, when the registration request is created. Stable across redeliveries | UUID |
| `cohort` | One cohort | Upstream, from whatever defines the cohort | Not ours to set |

`cohort` is a working name and may change.

## Value sets used

`Task.intent` takes the FHIR R4 value set unchanged, as does `Task.status` on a
lifecycle Task. On the registration request `status` is fixed to `requested`, and
on the response to `accepted`.

- status: `draft`, `requested`, `received`, `accepted`, `rejected`, `ready`,
  `cancelled`, `in-progress`, `on-hold`, `failed`, `completed`,
  `entered-in-error`
- intent: `unknown`, `proposal`, `plan`, `order`, `original-order`,
  `reflex-order`, `filler-order`, `instance-order`, `option`

`Task.priority` is constrained from the FHIR `request-priority` set to
`routine` and `asap`. `urgent` and `stat` are rejected with `INVALID_CODE`.

`Task.code`, `Task.businessStatus` and `Task.statusReason` take value sets we
define, in the code systems above.

## Reference

FHIR R4 resources:

- Task: https://hl7.org/fhir/R4/task.html
- ServiceRequest: https://hl7.org/fhir/R4/servicerequest.html
- Patient: https://hl7.org/fhir/R4/patient.html
- NamingSystem: https://hl7.org/fhir/R4/namingsystem.html

The `fhir.hl7.org.uk` and `fhir.nhs.uk` profiles and references:

| Profile | Canonical identifier | Version | Where to read it |
|---|---|---|---|
| UKCore-Patient | `https://fhir.hl7.org.uk/StructureDefinition/UKCore-Patient` | 2.5.0 | package `fhir.r4.ukcore.stu2` 2.1.0 |
| UKCore-ServiceRequest | `https://fhir.hl7.org.uk/StructureDefinition/UKCore-ServiceRequest` | 2.5.0 | package `fhir.r4.ukcore.stu2` 2.1.0 |
| England-Patient-PDS | `https://fhir.nhs.uk/England/StructureDefinition/England-Patient-PDS` | 0.0.1, draft | `NHSDigital/NHSEngland-FHIR-Programme-PDS`, `structuredefinitions/` |

- UK Core packages: https://simplifier.net/packages/fhir.r4.ukcore.stu2
- UK Core browsable: https://simplifier.net/hl7fhirukcorer4
- UK Core governance: https://digital.nhs.uk/services/fhir-uk-core

System URI namespaces, including HealthStore's, do not resolve to web addresses.

Value sets:

- `Task.status`: https://hl7.org/fhir/R4/valueset-task-status.html
- `Task.intent`: https://hl7.org/fhir/R4/valueset-task-intent.html
- `Task.priority`, the request-priority set: https://hl7.org/fhir/R4/valueset-request-priority.html

Patterns:

- Request: https://hl7.org/fhir/R4/request.html
