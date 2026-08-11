# Known gaps

## General

- Non-functional requirements are not specified: timeliness, rate limits and volumes.
- In the current service request, the patient's registered GP is included - is this relevant / required?
- The area around what is required related to the following needs to be considered:
  - The body that licenses e.g. commissioning body ODS
  - The practitioner or organisation registering ODS and site ODS
  - The practitioner responsible for care
  - Condition (eg SNOMED)
  - "Pathway" / programme of care
  - Caseloads / case management
  - Submodule of digital therapeutic product

## Luscii

Mapped against `POST /v1/patients`.

| Luscii field | Required | Carried by us |
|---|---|---|
| `firstName` | Yes | `Patient.name.given[0]` |
| `lastName` | Yes | `Patient.name.family` |
| `email` | Yes | `Patient.telecom` where `system=email` |
| `sex` | Yes | `Patient.gender` (potentially resolvable) |
| `accountName` | Yes | Nothing. Must be globally unique on Luscii |
| `organizationId` | Yes | Nothing |
| `groupId` | Yes | Nothing |
| `programId` | Yes | Nothing |
| `protocolId` | Yes | Nothing |
| `dateOfBirth` | No | `Patient.birthDate` |
| `phoneNumber` | No, unless SMS login | `Patient.telecom` where `system=phone` |
| `identifiers` | No | `Patient.identifier:nhsNumber` |
| `addressCountry` | No | `Patient.address[0].country` |
| `addressCity`, `addressPostalCode`, `addressStreet`, `addressHouseNumber`, `addressHouseNumberSuffix` | No | Nothing |
| `language` | No | Nothing |
| `timezone` | No | Nothing |
| `middleName` | No | Nothing. `given[0]` takes the first given name only |
| `patientNumber` | No | Nothing |
| `comments` | No | Nothing |
| `smsLoginEnabled` | No | Nothing |

### Licensing
Licences / cost attribution is not yet understood.
