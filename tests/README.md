# Integration & contract-validation tests

Runs the two example apps (`examples/reference-supplier`, `examples/healthstore-simulator`) against each other for real, over HTTP, and checks the result two different ways.

```sh
npm run test:integration
```

## Directories

### `contract-validation/`

Every request in this directory is routed through a [Prism](https://github.com/stoplightio/prism) proxy sitting in front of the real app, so it's validated against the OpenAPI spec at runtime as well as asserted on by the test. Use this directory for spec-conformant requests: the happy path, and business-logic negative cases (unknown id, wrong credentials) that are still schema-valid.

- `healthstore-api.test.js` — proxied through `specification/healthstore-api.yaml`, calling `healthstore-simulator`.
- `supplier-api.test.js` — proxied through `specification/supplier-api.yaml`, calling `reference-supplier`.

### `integration/`

Every request here goes straight to the real app, bypassing Prism entirely. Use this directory for deliberately spec-violating requests — missing headers, invalid enum values, a body that breaks a `oneOf`. If these went through the proxy, Prism would flag them as violations too, but that's the point of the test, not a bug: it would show up as noise in the violation log described below and make a passing run look broken.

- `healthstore-api.test.js` — calls `healthstore-simulator` directly.
- `supplier-api.test.js` — calls `reference-supplier` directly.

**Rule of thumb:** if the request should fail validation against the spec, put it in `integration/`. Otherwise put it in `contract-validation/`.

### `setup/`

Shared infrastructure, not tests:

- `global-setup.js` / `global-teardown.js` — Jest's `globalSetup`/`globalTeardown` hooks (wired up in `jest.config.js`). Setup builds both example apps with Gradle, then starts four processes: `reference-supplier` (port 8080), `healthstore-simulator` (port 8090), and a Prism proxy in front of each (ports 4013 and 4012 respectively). Teardown shuts them down and scrapes both proxies' logs for anything Prism flagged as a spec violation.
- `simulator-client.js` — token exchange, registration seeding, and `authHeaders(token)` for the two `healthstore-api.test.js` files. `/oauth2/token` and `/_simulator/*` are simulator control-plane endpoints, not part of `specification/healthstore-api.yaml` — there's no proxy route for them, so both directories call the simulator directly for setup regardless of which one they're testing through.
- `supplier-client.js` — token exchange, `authHeaders(token)`, and `buildSpecificTask`/`buildAvailableTask` for the two `supplier-api.test.js` files. The builders return `{ task, ...echoedValues }` with sensible defaults for a spec-conformant Task; pass an override object to change a field, or set one to `undefined` to delete it — that's how the negative tests build a Task that violates the spec (e.g. `buildSpecificTask({ priority: undefined })`).

### `postman/`

Unrelated: a Postman collection for the partner-shared DTx Integration API. See [`postman/README.md`](postman/README.md).

## How a spec violation gets caught

Requests are never blocked at the proxy — `global-setup.js` deliberately doesn't pass Prism the `--errors` flag. Blocking would make Prism drop the field-level detail of *why* a request or response violates the spec, keeping only a generic "invalid" error. Instead, every request is allowed through to the real app, and Prism's validation log is scraped for violations after the run. If it finds any, `global-teardown.js` prints them and the process exits non-zero — even if every Jest assertion passed, because the violation may be on a request no test directly asserts on (e.g. `healthstore-simulator`'s own outbound call to `reference-supplier`).

This means a **fully green Jest run can still fail the overall command** if Prism caught a violation. Check the teardown output, not just the Jest summary.

## Known gaps pinned by these tests

A few tests assert what the reference apps actually do today, not what the spec promises, because the alternative is a permanently red test until someone fixes the app:

- `POST /oauth/token` never returns the `401` the spec documents for "Client authentication failed" — every credential failure comes back as `400 invalid_client`.
- `GET /registrations` with `_count` or `page` out of range returns an uncaught `500` instead of the spec's `400` (`ConstraintViolationException` isn't handled by `ApiExceptionHandler`).

Search for `should be` in `integration/healthstore-api.test.js` to find these.
