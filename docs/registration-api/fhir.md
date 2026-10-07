# Registrations API: FHIR resources and fields

## 1. FHIR R4 Patient: provides demographics not available from NHS Login

This is profiled on `UKCore-Patient` and returned by `GET /registrations` contained in a ServiceRequest.

As described in the [change log](change-log.md), all demographic data is to be sourced
from NHS login, to the extent that your current scopes allow. While waiting for NHS login
to provision all required scopes, the Registrations API provides these demographics in a FHIR Patient resource
as an interim state.

**The Patient resource itself is not interim state.** Gender is not available from NHS Login so must always be provided.

| Data | Target source — your `/userinfo` call | Scope | Until NHS login provision the scope |
|---|---|---|---|
| NHS number | `nhs_number` | `profile` | n/a - default scope |
| Last name | `family_name` | `profile` | n/a - default scope |
| Date of birth | `birthdate` | `profile` | n/a - default scope |
| First name(s) | `given_name` | `profile_extended` | Interim. `Patient.name.given` |
| Email | `email` + `email_verified` — verified, guaranteed | `email` | Interim. `Patient.telecom` PDS-sourced, 0..1, best-effort |
| Phone | `phone_number` + verified/PDS-match flags — verified, guaranteed | `phone` | Interim. `Patient.telecom` PDS-sourced, 0..1, best-effort |
| Gender | Not available from NHS login | n/a | Not interim. `Patient.gender` will always be used  |

For each item, use the `/userinfo` claim when your scopes provide it and fall back to the Patient resource otherwise.
This logic is the same before and after NHS login provision the scopes, so there is no formal cutover when the new scopes are provisioned.
This spec supports both interim and target states simultaneously. The interim fields in the FHIR Patient resource are marked as deprecated
in the specification and will be removed when appropriate.

>⚠️ **Administrative gender is not clinical sex.**
>
> The value is PDS administrative gender, one of [four codes](https://archive.datadictionary.nhs.uk/DD%20Release%20May%202024/attributes/person_gender_code.html). Mapping this to the DTx platform's model
> must be done by the DTx supplier. This field should not be used for clinical purposes.

## 2. FHIR R4 ServiceRequest: provides the remaining registration data

This is profiled on `UKCore-ServiceRequest` and returned by `GET /registrations`

### 2.1 ServiceRequest: interim data shape

This example shows the interim demographic fields in the contained patient resource,
as described in Section 1, above.

```json
{
  "resourceType": "ServiceRequest",
  "meta": { "profile": ["https://fhir.hl7.org.uk/StructureDefinition/UKCore-ServiceRequest"] },
  "extension": [{"url": "https://fhir.dtx.national.nhs.uk/StructureDefinition/Extension-ContractingOrganisation",
                "valueIdentifier": { "system": "https://fhir.nhs.uk/Id/ods-organization-code", "value": "QWU" }}] // contracting_org_ods: the commissioner's ODS code
  "contained": [
        { "resourceType": "Patient",
          "id": "patient",
          "gender": "female", // Sourced from PDS. Administrative Gender
          "name": [{ "use": "official", "given": ["Jane"] }], // Sourced from PDS. Last name already sourced from /userinfo. Given name requires scope: profile_extended
          "telecom": [
            { "system": "email", "value": "jane.example@example.com" }, // Sourced from PDS. Requires scope: email
            { "system": "phone", "value": "07700 900123" }] // Sourced from PDS. Requires scope: phone
    }
  ],
  "identifier": [{ "system": "https://fhir.dtx.national.nhs.uk/Id/registration", "value": "7e1f4c8d-2b5a-49e0-8c6f-1d3a9b0e5f27" }],
  "status": "active",
  "intent": "order",
  "code": { "coding": [{ "system": "https://fhir.dtx.national.nhs.uk/CodeSystem/intervention-<DTx supplier name>", "version": "2026-09", "code": "hypertension-remote-monitoring" }] },
  "subject": { "reference": "#patient" }
}
```

> ⚠️ **This ServiceRequest cannot be used to identify the patient.**
>
> As required by the [retrieve operation](../../specification/components/endpoints/getRegistrations.yaml) in the OpenAPI specification, the fetch from the Registrations API must happen immediately after
> the fetch from NHS login /userinfo, with both operations running synchronously in the same process.
> **The patient must be identified using the NHS Number from the preceding /userinfo response.**
>
> `"id: "patient"` is a label used solely to find the contained patient. It is not a placeholder in this example for NHS Number or any other patient ID.


### 2.2 ServiceRequest: target data shape

The target ServiceRequest shape is identical, but omits the three interim demographic fields that
are sourced from the NHS login /userinfo endpoint once the DTx supplier has been granted
the `profile_extended`, `email` and `phone` scopes by NHS login.


```json
{
  "resourceType": "ServiceRequest",
  "meta": { "profile": ["https://fhir.hl7.org.uk/StructureDefinition/UKCore-ServiceRequest"] },
  "extension": [{"url": "https://fhir.dtx.national.nhs.uk/StructureDefinition/Extension-ContractingOrganisation",
                "valueIdentifier": { "system": "https://fhir.nhs.uk/Id/ods-organization-code", "value": "QWU" }}] // contracting_org_ods: the commissioner's ODS code
  "contained": [{ "resourceType": "Patient", "id": "patient", "gender": "female" }],
  "identifier": [{ "system": "https://fhir.dtx.national.nhs.uk/Id/registration", "value": "7e1f4c8d-2b5a-49e0-8c6f-1d3a9b0e5f27" }],
  "status": "active",
  "intent": "order",
  "code": { "coding": [{ "system": "https://fhir.dtx.national.nhs.uk/CodeSystem/intervention-example-supplier", "version": "2026-09", "code": "hypertension-remote-monitoring" }] },
  "subject": { "reference": "#patient" }
}
```


### 2.3 ServiceRequest: reaching the contained resources

A reference beginning `#` is a reference to the entry in this
resource's own `contained` array whose `id` matches the text after the `#`.

Javascript example

```javascript
// JavaScript
const REGISTRATION_ID_SYSTEM = 'https://fhir.dtx.national.nhs.uk/Id/registration';
const CONTRACTING_ORG_EXT = 'https://fhir.dtx.national.nhs.uk/StructureDefinition/Extension-ContractingOrganisation';

const resolve = (resource, ref) =>
  resource.contained.find(c => '#' + c.id === ref.reference);

const patient  = resolve(sr, sr.subject);

const registration = {
  registrationId:    sr.identifier.find(i => i.system === REGISTRATION_ID_SYSTEM).value,
  interventionCode:  sr.code.coding[0], // { system, version, code }
  contractingOds:    sr.extension.find(e => e.url === CONTRACTING_ORG_EXT).valueIdentifier.value,
  gender:            patient.gender,
  // Interim demographics if present
  givenNames: patient.name?.find(n => n.use === 'official')?.given,
  email:      patient.telecom?.find(t => t.system === 'email')?.value,
  phone:      patient.telecom?.find(t => t.system === 'phone')?.value,
};
```

FHIR libraries, e.g. HAPI, do this resolution for you. The code above shows how
to roll your own if required.

### 2.4 ServiceRequest: where the intervention code comes from

This field has been renamed from `care_path_code` in the v0.1 draft, renamed following
supplier feedback. The term "intervention" names what the field actually identifies, i.e. the
thing being prescribed. It is also the established term in NHS evidence and
commissioning vocabulary: NICE classifies digital health technologies by
intervention.

#### 2.4.1 The intervention catalogue

The code comes from a catalogue each DTx supplier publishes to HealthStore: a
versioned list of the interventions the supplier makes available for commissioning.
Each entry carries a supplier-issued intervention code, a display name for clinicians, and the
condition it serves. As with every other call in this integration, publication is a
call from the DTx supplier's platform to HealthStore - a model intended to avoid
suppliers hosting and maintaining a dedicated endpoint.

Which interventions can be requested for a condition, under a given commissioning
arrangement, is agreed with the commissioner and configured from the catalogue at
onboarding. The requesting clinician always sees and confirms the defined
intervention before the registration is created: the code carried in
`ServiceRequest.code` is a direct selection by a clinician from the latest version of
the chosen supplier's intervention catalogue.

#### 2.4.2 What a catalogue could look like

A catalogue can be as coarse or as fine as the supplier's products are. For example:

**Supplier A — two products, product-level granularity:**

| Code | Display name | Condition served |
|---|---|---|
| `prod-copd` | Product 1 | COPD |
| `prod-hf` | Product 2 | Heart failure |

**Supplier B — one product, intervention-level granularity:**

| Code | Display name | Condition served |
|---|---|---|
| `copd-standard` | COPD self-management — standard protocol | COPD |
| `copd-enhanced` | COPD self-management — enhanced monitoring | COPD |
| `hf-remote` | Heart failure remote monitoring | Heart failure |
| `ht-remote` | Hypertension remote monitoring | Hypertension |

The level of granularity is the supplier's. HealthStore never interprets the code,
and treats both of these catalogues identically: a registration carries one code
from the catalogue, whichever shape the supplier chose. For a supplier shaped like
A, the entire catalogue could be a table this size, defined during onboarding
and only updated if a new intervention is added.

#### 2.4.3 Clinical terminology

A clinical terminology layer for these interventions (dm+d / SNOMED CT) is being
defined in a separate programme workstream with NHSBSA. Its conclusions will attach
alongside catalogue entries when they land; they will not replace the code carried
in `ServiceRequest.code`, which is stable for the life of the integration.

#### 2.4.4 Design Principles

The catalogue is designed so that HealthStore is never between a DTx supplier and
its own products:

- **Suppliers publish whenever they choose.** Publication is a supplier-initiated
  call. A new version takes effect on receipt.
- **Additions are non-breaking by construction.** New entries extend the catalogue;
  existing codes are stable, and registrations issued against an earlier version
  remain valid. `ServiceRequest.code` has built-in versioning so both
  sides always know which version a registration was created against. dm+d/SNOMED CT annotations attach to
  catalogue entries as they are allocated.
- **Commissioned means immediately visible.** Adopting a published intervention
  into a commissioning arrangement is a config-only change. Once
  commissioned and published to HealthStore, it appears to clinicians immediately.

### 2.5 ServiceRequest: contracting organisation

The organisation that procures the DTx, referred to across this programme as
the commissioner, is identified on each registration as `contracting_org_ods`.
This is deliberately not the Data Dictionary's ORGANISATION IDENTIFIER (CODE OF
COMMISSIONER): this field identifies the organisation the DTx supplier
contracts with and charges, which in private beta is the same organisation but
need not remain so.

It is carried as an extension on the ServiceRequest, with `valueIdentifier`
holding the ODS code:

```json
"extension": [{
  "url": "https://fhir.dtx.national.nhs.uk/StructureDefinition/Extension-ContractingOrganisation",
  "valueIdentifier": { "system": "https://fhir.nhs.uk/Id/ods-organization-code", "value": "QWU" }
}]
```

It is an extension rather than a contained `Coverage` referenced from
`insurance`, for three reasons:

1. UK Core does not profile Coverage. Introducing an unprofiled US-realm
   financial resource invites interoperability challenge, whereas
   realm-specific extensions are UK Core's own standard mechanism for
   concepts the base resources do not carry.
2. The extension keeps the payload to a single contained resource, with no
   `insurance` element and no `beneficiary` back-reference.
3. The name. "Commissioning organisation" collides with the Data Dictionary
   element above, which means the statutory commissioner of activity.

`Extension-UKCore-Coverage` is not an alternative either. It holds a funding
category of three codes and cannot name an organisation.

### 2.6 ServiceRequest: Tolerant reader pattern

DTx platforms must ignore response elements they do not recognise and process the registration
as they otherwise would. Future additions are additive and non-breaking.
Nothing is renamed or repurposed within a major version.

## 3. FHIR R4 Task: Registration outcome

A `Task`, submitted by the platform against the registration, from inside the
patient's session. Same path and shape as v0.1.

| Element | Value | Notes |
|---|---|---|
| `resourceType` | `Task` | |
| `status` | A `task-status` value | The Task's own workflow status, not the registration's state. v0.1 used `accepted` with registered and `rejected` with rejected; either remains valid |
| `intent` | A `task-intent` value | `order` |
| `businessStatus` | `registered` or `rejected` | The registration's state |
| `statusReason` | An acknowledgement reason | Required with `rejected`. Optional with `registered`, where the only valid value is `already-registered` |
| `note[0].text` | Free text, no patient data | Required where `statusReason` is `other` |

Because the Task can only be sent from inside the patient's authenticated
session, `registered` proves the patient has signed in.

### 3.1 Registration outcome reasons

System `https://fhir.dtx.national.nhs.uk/CodeSystem/acknowledgement-reason`.

| `code` | With | Meaning |
|---|---|---|
| `already-registered` | `registered` | The platform already held an account for this patient and intervention and linked it. A success; the registration is fulfilled |
| `licence-exhausted` | `rejected` | No licence available. The contract schedule warrants licence availability, so this is treated as an incident |
| `intervention-not-configured` | `rejected` | The intervention code is not configured for the commissioning organisation, or the organisation is not known to the platform |
| `other` | `rejected` | Any other reason. `note` is required |

The rules relating `statusReason` to `businessStatus`, and `note` to `other`,
are checked by HealthStore; the schema alone cannot express them. Post-
registration states and their closure reasons are in
[`lifecycle.md`](lifecycle.md).

## 4. System URIs

A system URI appears only on elements that are codes or identifiers.

| Resource | Element | FHIR type | System URI |
|---|---|---|---|
| ServiceRequest | `identifier` | `Identifier` | `https://fhir.dtx.national.nhs.uk/Id/registration` |
| ServiceRequest | `code` | `CodeableConcept` | `https://fhir.dtx.national.nhs.uk/CodeSystem/intervention-<platform>`, one per platform, named at onboarding |
| ServiceRequest | `extension[ContractingOrganisation].valueIdentifier` | `Identifier` | `https://fhir.nhs.uk/Id/ods-organization-code` |
| Patient | `gender` | `code` | None; `administrative-gender` |
| Task | `businessStatus` | `CodeableConcept` | `https://fhir.dtx.national.nhs.uk/CodeSystem/registration-business-status` |
| Task | `statusReason` | `CodeableConcept` | `https://fhir.dtx.national.nhs.uk/CodeSystem/acknowledgement-reason` |

Each DTx `system` value is fixed. v0.1 used `https://fhir.healthstore.nhs.uk/...`;
the rename is a constant swap on the platform side, made before the tag so
that it is never a breaking change afterwards. The ODS code system is NHS
England's and is unchanged.

The contracting organisation extension is identified by its canonical url,
`https://fhir.dtx.national.nhs.uk/StructureDefinition/Extension-ContractingOrganisation`.
That url is the definition of the extension, not a `system`; the `system` on
its `valueIdentifier` is the ODS row above. The StructureDefinition is in
[`specification/fhir/`](../../specification/fhir/StructureDefinition-Extension-ContractingOrganisation.json).

### 4.1 Identifier values

| System | Identifies | Issued by | Format |
|---|---|---|---|
| `registration` | One registration | HealthStore, when the registration is created. Stable for its life | UUID |

## 5. Value sets used

- `ServiceRequest.status`: `active`. Only open registrations are returned.
- `ServiceRequest.intent`: `order`.
- `Patient.gender`: `administrative-gender`, unchanged.
- `Task.status`, `Task.intent`: the FHIR R4 sets, unchanged.
- `Task.businessStatus`, `Task.statusReason`: the code systems above.

## 6. Reference

FHIR R4 resources:

- ServiceRequest: https://hl7.org/fhir/R4/servicerequest.html
- Patient: https://hl7.org/fhir/R4/patient.html
- Task: https://hl7.org/fhir/R4/task.html

The `fhir.hl7.org.uk` profiles:

| Profile | Canonical identifier | Version | Where to read it |
|---|---|---|---|
| UKCore-ServiceRequest | `https://fhir.hl7.org.uk/StructureDefinition/UKCore-ServiceRequest` | 2.5.0 | package `fhir.r4.ukcore.stu2` 2.1.0 |
| UKCore-Patient | `https://fhir.hl7.org.uk/StructureDefinition/UKCore-Patient` | 2.5.0 | package `fhir.r4.ukcore.stu2` 2.1.0 |

- UK Core packages: https://simplifier.net/packages/fhir.r4.ukcore.stu2
- UK Core browsable: https://simplifier.net/hl7fhirukcorer4
- UK Core governance: https://digital.nhs.uk/services/fhir-uk-core

NHS login:

- Scopes and claims: https://nhsconnect.github.io/nhslogin/scopes-and-claims/
- Discovery: `/.well-known/openid-configuration` on each environment's
  issuer, listed in [`resources.md`](resources.md)

System URI namespaces, including HealthStore's, do not resolve to web addresses.

Value sets:

- `Task.status`: https://hl7.org/fhir/R4/valueset-task-status.html
- `Task.intent`: https://hl7.org/fhir/R4/valueset-task-intent.html
- `administrative-gender`: https://hl7.org/fhir/R4/valueset-administrative-gender.html
