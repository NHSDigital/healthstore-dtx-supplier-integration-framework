# HealthStore DTx supplier integration

The integration contract between HealthStore and a digital therapeutics (DTx)
supplier's platform: the Registration API's OpenAPI specification, its FHIR R4
definitions, the examples, the tests that prove them, and the documentation.

>### Scope: PRIVATE BETA

## The integration at a glance

![v1.0 registration sequence](docs/registration-api/registration-sequence.png)

1. A clinician requests a DTx for a patient in HealthStore. The patient is
   invited by NHS App message or SMS, with a link to your app.
2. The patient opens your app and signs in with NHS login, as today.
3. Your backend calls NHS login `/userinfo` for the patient's demographics,
   verified at source.
4. On every NHS login sign-in, your backend calls `GET /registrations`
   with the patient's ID token in the `NHSD-ID-Token` header and receives
   their open registrations.
5. Your backend acknowledges each registration, registered or rejected with a
   coded reason, seconds later in the same sign-in.

Two outbound calls from your platform, inside the patient's sign-in. You host
no inbound API, run no queue and do no polling.

## Three things to know

**How the patient reaches your app.** A standard App Link / Universal
Link from a dedicated HealthStore page in the NHS App. Your app opens with no
context and the patient signs in with NHS login. There is no app-to-app
sign-on integration to build for private beta. Because you retrieve on every
NHS login sign-in, a patient with an open registration is registered even if
they reach your product without the NHS App; for anyone else the retrieve
returns an empty Bundle.
[How and why this changed from the draft](docs/registration-api/change-log.md#1-how-the-patient-reaches-your-product)

**Registration is synchronous, at sign-in.** One GET request, inside the
patient's session, with only the patient's ID token in the `NHSD-ID-Token` header. There is
no state to reconcile between HealthStore and your platform, and a registration
that is retrieved but not acknowledged is simply presented again at the next sign-in.
A single OAuth2 endpoint provides access tokens for all interactions.
[How and why this changed from the draft](docs/registration-api/change-log.md#2-registration-is-synchronous-at-sign-in)

**A registration comprises four fields, plus an interim set of three.** The
four are the registration identifier, an intervention code from your own
published catalogue, the contracting organisation's ODS code, and the
patient's administrative gender. Until NHS login grants you the
`profile_extended`, `email` and `phone` scopes, it also carries the patient's
given name, email and phone. Everything else about the patient comes from
your own NHS login `/userinfo` call.
[How and why this changed from the draft](docs/registration-api/change-log.md#3-what-a-registration-carries)

## Documents

- [Design overview](docs/registration-api/overview.md): the model, flow, and rationale
- [FHIR resources and fields](docs/registration-api/fhir.md): the ServiceRequest, contained Patient, Task, and what comes from NHS login instead
- [Resources, authentication, errors](docs/registration-api/resources.md)
- [What changed from the v0.1 draft, and why](docs/registration-api/change-log.md): with the field reference and the interim annex
- [Lifecycle](docs/registration-api/lifecycle.md): fast follow as a versioned change
- [v0.1 open questions](docs/registration-api/open-questions-and-decisions.md): superseded by the [change log](docs/registration-api/change-log.md), kept for the record

## The specification

The OpenAPI document is `specification/healthstore-api.yaml`, assembled from
`specification/components/` by reference. The FHIR StructureDefinitions the
payload uses are in `specification/fhir/`.

### Build against tags, not branches

The payload contract is change-controlled from v1.0. Build against a release
tag (`v1.0.0` and later), never against a branch. Additions within a major
version are additive and non-breaking; your parser ignores response elements
it does not recognise.

## Validating the specification

`specification/healthstore-api.yaml` is
linted with [Spectral](https://github.com/stoplightio/spectral), configured in
`.spectral.yaml`. Contract tests run the specification's examples through a
[Prism](https://github.com/stoplightio/prism) mock, and the FHIR examples are
validated against UK Core with the HL7 validator in CI; see
[`tests/README.md`](tests/README.md).

```sh
npm install
npm run lint:spec
npm run test:integration
```

## Archived material

- [docs/archive](docs/archive): v0.1 draft documents and the deprecated DTx
  Integration API with its Postman collection, superseded in full.
- [examples/reference-supplier](examples/reference-supplier): reflects the
  v0.1 draft Integration API and is no longer built. A v1.0 reference
  implementation accompanies conformance testing.

## How to contribute

- NHS Digital: raise an issue or open a PR; see `.github/PULL_REQUEST_TEMPLATE.md`.
- Collaborators: fork the repository and open a pull request.
- Review ownership: `.github/CODEOWNERS`.
- Security disclosure: `.github/SECURITY.md`.

## Licence

MIT; see `LICENCE.md`. HTML/Markdown documentation is © Crown Copyright
and available under the Open Government Licence v3.0.
