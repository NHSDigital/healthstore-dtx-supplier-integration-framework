# Open questions and decisions

## Open questions

### Patient consent

- Is there a distinction between platforms that will not want to ingest the full
  registration, in respect of patient data, and those that can?
- If that distinction is required, does it depend on whether the registration
  arises from cohorting or from direct clinician single patient enrolment?
- Should the Registrations API support a parameter that excludes the full patient
  record from responses? And the inverse: should the default be minimal, with the
  full record returned only when a parameter asks for it?
- Where does ownership of the decision to return full patient data sit, with the
  client or with HealthStore?

### Care path code

`care_path_code` is in the schema, on `ServiceRequest.code`. Its construct is not
agreed. Settling it needs both suppliers and the HealthStore programme.

- Whether the care path code uses dm+d or a combined pathway code.
- Whether it is a single value or decomposes into parts.
- How a platform's own identifiers for organisation, group, programme and protocol
  map onto it, where a platform uses several and another uses one.
- Whether a platform serves a catalogue of the codes available to it.
- What the levels beneath it are called. Pathway, programme, protocol, condition
  and module are each used differently across the platforms.

### Practitioner identities

- Which practitioner is the `requester`, the one placing the enrolment or the
  clinician they are placing it on behalf of.
- Where the practitioner responsible for care sits, given they may differ from the
  one placing the enrolment.
- Whether a site ODS code is needed in addition to the clinician's organisation.

### Non-functional requirements

None of these is set. They are programme-set and standard across integrations.

- Rate limit values for the Registrations API and the supplier API, and whether a
  limit is counted per endpoint or across the whole surface.
- Timeliness targets for a single patient registration and for cohort processing.
- Maximum cohort size, page size, and peak and mean daily registration volumes.
- A minimum rate at which registrations are retrieved, or a maximum time before a
  cohort is fully consumed.
- The rate at which HealthStore may send registration requests, and what follows a
  failure to deliver.

### For the HealthStore programme

- Whether cohort membership is fixed at release or changes between calls.
- Whether a patient can appear in more than one cohort.
- Whether `cohort` is the right name, against study or caseload or group.
- Whether the registered GP is mandatory, given a patient may have no registered
  practice.
- Whether GP-level reporting is required.
- Which reporting-driven fields the registration includes, given management
  information is out of scope.
- How a patient enrolled in secondary care and later targeted by primary care is
  recognised, and what mitigates it.
- Which error classes on the Registrations API are terminal and which are
  retryable, and with what schedule. This is the supplier retrying inbound,
  distinct from HealthStore's own outbound retries, which `overview.md` already
  states are HealthStore's own.

### For suppliers

- The full list of scenarios in which a registration is cancelled or deactivated,
  including whether either can be time-based.

#### Luscii

From mapping against `POST /v1/patients`. Fields the contract already includes are
not listed. The account name and the pathway identifiers are under agreement
below.

- Whether `Patient.gender` satisfies a platform requiring sex.
- Whether every given name is needed, since only the first is included.
- Whether a language is required, which nothing currently provides.

### For HealthStore and suppliers to agree

- Whether cancelled is a rejection, a distinct business status, or `deactivated`
  with a reason.
- The set of rejection reasons. The reason goes in `statusReason`, with two codes
  in the schema so far: not licensed, and duplicate.
- What a platform returns when the same patient arrives from more than one cohort,
  so that HealthStore stops retrying.
- Whether a platform pre-provisions for an unrecognised organisation or rejects the
  registration.
- What happens where a clinician has no route to retract a registration.
- Whether natural completion of a therapeutic course is the same signal as
  de-registration, or a distinct one.
- Whether HealthStore signals de-registration outbound, and in what form.
- Whether de-registration removes one registration or the patient's account.
- How duplicates are removed within a cohort.
- What HealthStore supplies where a platform requires a globally unique account
  name.

## Proposals

Not decided. Presented for reaction.

### Identifying the clinician

The registration names the clinician responsible for the patient's care on the
platform. The practitioner who places the enrolment is internal to HealthStore and
does not appear.

`requester` references a contained `PractitionerRole`, which links the clinician to
their organisation. Both give an `identifier` and a `display`, so a platform
receives the code and the name together.

Where no individual clinician is responsible, `requester` references the
organisation on its own.

```json
{
  "resourceType": "ServiceRequest",
  "contained": [
    { "resourceType": "Patient", "id": "patient" },
    {
      "resourceType": "PractitionerRole",
      "id": "responsible",
      "practitioner": {
        "identifier": {
          "system": "https://fhir.nhs.uk/Id/sds-user-id",
          "value": "555050304199"
        },
        "display": "P Raman"
      },
      "organization": {
        "identifier": {
          "system": "https://fhir.nhs.uk/Id/ods-organization-code",
          "value": "A81001"
        },
        "display": "Example Medical Practice"
      }
    }
  ],
  "status": "active",
  "intent": "order",
  "subject": { "reference": "#patient" },
  "requester": { "reference": "#responsible" },
  "performer": [
    {
      "identifier": {
        "system": "https://fhir.nhs.uk/Id/ods-organization-code",
        "value": "RX898"
      },
      "display": "Example Digital Therapeutics"
    }
  ]
}
```

This is base FHIR R4 and published NHS naming systems.

Two details this leaves open. The professional identifier system, shown as an SDS
user ID, where a GMC or NMC number may be what is available at enrolment. And
whether `PractitionerRole.code` is populated, if a platform needs the job role as
well as the identity.

`performer` is unchanged. Only `requester` changes, from an ODS organisation code to
a contained reference, which is a breaking change.

### Naming the commissioning body

The registration names the commissioning body so a platform can check it is
licensed to serve that region. `insurance` references a contained `Coverage`, whose
`payor` names the organisation by ODS code. FHIR defines `insurance` for coverage,
pre-authorisation and pre-determination.

`Coverage` requires `status`, `beneficiary` and `payor`. `beneficiary` references
the patient already contained, so it adds no further resource.

Added to the resource above:

```json
{
  "contained": [
    {
      "resourceType": "Coverage",
      "id": "commissioning",
      "status": "active",
      "beneficiary": { "reference": "#patient" },
      "payor": [
        {
          "identifier": {
            "system": "https://fhir.nhs.uk/Id/ods-organization-code",
            "value": "QWU"
          },
          "display": "Example Integrated Care Board"
        }
      ]
    }
  ],
  "insurance": [{ "reference": "#commissioning" }]
}
```

`Extension-UKCore-Coverage` is not the alternative. It holds a funding category of
three codes, `nhs`, `private` and `devolved-nations`, and cannot name an
organisation.

## Decisions

- **Condition implies module.** Sub-modules are not modelled. HealthStore sends the
  condition or care pathway and the platform resolves how that maps internally.
  Programmes and their protocols are configured upstream at mobilisation, and the
  clinician placing a patient does no configuration.
- **The registered GP is retained.** `Patient.generalPractitioner` is a fact about
  the patient rather than a party to the digital therapeutic, and may be distinct from
  the clinician responsible for their care.
- **Caseload assignment is the platform's own.** The registration includes no
  caseload or clinical team identifier. A platform resolves assignment from the
  rest of the registration.
- **The patient demographics set is settled.** The fields on the contained Patient
  were reviewed with suppliers and confirmed as required.
- **The registration request is pushed, the registration is pulled.** The request
  contains no patient data. HealthStore does not push patient data to a platform,
  and a face to face registration does not wait on a poll interval.
- **A platform is told a registration is coming.** Registration is not triggered by
  the patient opening the NHS App. The platform is notified in advance and
  retrieves on its own schedule.
- **Activation completes the initial registration.** The platform reports the
  registration registered or rejected, and later activated. Activation means the
  patient has done whatever initial setup the product requires and can use it.
  Later lifecycle events are separate.
- **Licensing is enforced on both sides.** HealthStore MUST NOT register a patient
  with a platform that is not licensed for the commissioning body, and a platform
  MUST reject a registration for a region it is not licensed to serve. The
  registration names the commissioning body so it can.
- **Management information is out of scope.** MI is handled separately and is not a
  purpose of this API.
- **Non-functional values are set by the programme.** Rate limits, timeliness,
  availability and volumes are standard across integrations and verified at
  onboarding, rather than set in this contract.
- **Cohort retrieval drains rather than pages.** There is no page parameter. The
  platform calls until nothing is returned.
- **The registration's state is in `businessStatus`, not `Task.status`.** A
  registration is registered once the platform confirms it, reported as
  `businessStatus` `registered` or `rejected`. `Task.status` is the FHIR workflow
  status of the Task itself. On the response to a registration request it is
  `accepted`, meaning only that the platform will retrieve.
- **De-registration is irreversible.** A de-registered registration is not
  reinstated. Putting the same patient back onto the same product is a new
  registration with a new registration identifier.
- **Off-boarding uses the lifecycle Task.** There is no separate off-boarding
  operation. A platform reports it through `POST /registrations/{id}/tasks` with
  `businessStatus` `deactivated`, as it reports acceptance and activation. The
  mechanism is in the specification; the narrative sits in these docs.
