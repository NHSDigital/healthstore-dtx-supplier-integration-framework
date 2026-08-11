# Integration & contract-validation tests

Runs `examples/reference-supplier` for real, over HTTP, and checks the result two different ways.

```sh
npm run test:integration
```

## Directories

### `contract-validation/`

Every request in this directory is routed through a [Prism](https://github.com/stoplightio/prism) proxy sitting in front of the real app, so it's validated against the OpenAPI spec at runtime as well as asserted on by the test. Use this directory for spec-conformant requests: the happy path, and business-logic negative cases (unknown id, wrong credentials) that are still schema-valid.

- `supplier-api.test.js` — proxied through `specification/supplier-api.yaml`, calling `reference-supplier`.

### `integration/`

Every request here goes straight to the real app, bypassing Prism entirely. Use this directory for deliberately spec-violating requests — missing headers, invalid enum values, a body that breaks a `oneOf`. If these went through the proxy, Prism would flag them as violations too, but that's the point of the test, not a bug: it would show up as noise in the violation log described below and make a passing run look broken.

- `supplier-api.test.js` — calls `reference-supplier` directly.

**Rule of thumb:** if the request should fail validation against the spec, put it in `integration/`. Otherwise put it in `contract-validation/`.

### `setup/`

Shared infrastructure, not tests:

- `global-setup.js` / `global-teardown.js` — Jest's `globalSetup`/`globalTeardown` hooks (wired up in `jest.config.js`). Setup builds the example app with Gradle, then starts two processes: `reference-supplier` (port 8080) and a Prism proxy in front of it (port 4013). Teardown shuts them down and scrapes the proxy's log for anything Prism flagged as a spec violation.
- `supplier-client.js` — token exchange, `authHeaders(token)`, and `buildSpecificTask`/`buildAvailableTask` for the two `supplier-api.test.js` files. The builders return `{ task, ...echoedValues }` with sensible defaults for a spec-conformant Task; pass an override object to change a field, or set one to `undefined` to delete it — that's how the negative tests build a Task that violates the spec (e.g. `buildSpecificTask({ priority: undefined })`).

### `postman/`

Unrelated: a Postman collection for the partner-shared DTx Integration API. See [`postman/README.md`](postman/README.md).

## No coverage of the Registrations API

`specification/healthstore-api.yaml` has no tests. The in-memory Health Store stand-in that served it has been removed, and nothing else implements the Registrations API. Spectral still lints the document, but nothing exercises it at runtime.

## How a spec violation gets caught

Requests are never blocked at the proxy — `global-setup.js` deliberately doesn't pass Prism the `--errors` flag. Blocking would make Prism drop the field-level detail of *why* a request or response violates the spec, keeping only a generic "invalid" error. Instead, every request is allowed through to the real app, and Prism's validation log is scraped for violations after the run. If it finds any, `global-teardown.js` prints them and the process exits non-zero.

This means a **fully green Jest run can still fail the overall command** if Prism caught a violation. Check the teardown output, not just the Jest summary.

## Known gaps pinned by these tests

A test asserts what the reference app actually does today, not what the spec promises, because the alternative is a permanently red test until someone fixes the app:

- `POST /oauth/token` never returns the `401` the spec documents for "Client authentication failed" — every credential failure comes back as `400 invalid_client`.

Search for `should be` in `integration/supplier-api.test.js` to find these.
