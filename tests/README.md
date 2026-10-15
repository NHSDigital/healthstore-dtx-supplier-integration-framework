# Contract-validation tests

Runs a [Prism](https://github.com/stoplightio/prism) mock of each
specification, `specification/healthstore-api.yaml` on port 4013 and
`specification/usage-api.yaml` on port 4014, and calls them the way a
supplier platform would.

```sh
npm run test:integration
```

## What is under test

Nothing in this repository implements the Registrations API; HealthStore
does. The mock is therefore the thing under test, and it proves two things:

- **The spec's examples conform to its schemas.** Prism serves each
  operation's examples and validates them on the way out. The tests then
  assert the shape a supplier depends on: four data-bearing fields on
  the registration, nothing v0.1 carried and v1.0 removed, the interim
  demographics on the contained Patient and nowhere else, an empty Bundle for
  no registrations, `OperationOutcome` error bodies, `Retry-After` on 429.
- **The request shapes a supplier will send are accepted, and the ones the
  contract removed are refused.** Prism validates every request against the
  spec. A missing `NHSD-ID-Token`, a missing `X-Request-ID`, a v0.1
  `businessStatus` or `statusReason`: each draws the operation's
  `400` with its `OperationOutcome` example. The assertion is that the spec
  rejects the shape.

## Directories

- `contract-validation/healthstore-api.test.js`: the Registrations API tests.
- `contract-validation/usage-api.test.js`: the Usage API tests, same
  approach. The NDJSON batch POST is not mocked: Prism parses bodies as JSON.
- `setup/global-setup.js`, `setup/global-teardown.js`: Jest's
  `globalSetup`/`globalTeardown` hooks, wired up in `jest.config.js`. Setup
  starts the two `prism mock` processes; teardown stops them.
- `fhir/extract-examples.js`: writes every FHIR resource in the spec's
  examples to JSON files for the HL7 validator, run by the `fhir-validate`
  CI job against base R4, `fhir.r4.ukcore.stu2` 2.1.0 and the
  StructureDefinitions in `specification/fhir/`.

## Choosing an example

Prism picks the first example unless told otherwise. The tests use the
`Prefer` header to pick a named example or a status code:

```
Prefer: example=none
Prefer: code=401, example=token-expired
```

## What this does not cover

- Behaviour the schema cannot express: `already-registered` only with
  `registered`, `note` required with `other`, repeat Tasks as no-ops. Those
  are HealthStore's checks and are covered by conformance testing.
- FHIR profile conformance. See the `fhir-validate` job in
  `.github/workflows/api-checks.yaml`.
