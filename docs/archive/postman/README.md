# Postman — DTx Integration API

Reference Postman collection for integrators submitting FHIR transaction Bundles to the partner-shared DTx Integration API.

Spec: [`dtx-integration.yaml`](../dtx-integration.yaml).

## Contents

- `collections/dtx-integration.postman_collection.json`: success path, idempotency 409, no-key 403, and 400 validation cases.
- `environments/partner-shared.postman_environment.json`: `baseUrl` and an empty `apiKey` slot.

## Prerequisites

**Postman desktop:** <https://www.postman.com/downloads/>.

**Newman CLI** (alternative): Node.js 18+. Run via `npx newman ...`; no install needed.

## Setup

1. Obtain your API key out of band.
2. In Postman: File → Import, drop in both JSON files.
3. Top-right environment picker → select `partner-shared`.
4. Open the environment, paste your key into `apiKey`, save.
5. Confirm `baseUrl` matches the partner-shared host you're targeting. The default is `https://ptr-shared-dtx-api.healthstore-alpha.uk`.

## Running

### Postman desktop

1. Open the `DTx Integration API` collection.
2. Either run requests individually, or use the Collection Runner (`...` menu → Run collection) to run them all top to bottom. Order matters for the duplicate check; see below.

### Newman CLI

From `tests/postman/`:

```sh
npx newman run collections/dtx-integration.postman_collection.json \
  -e environments/partner-shared.postman_environment.json \
  --env-var "apiKey=$YOUR_API_KEY"
```

With htmlextra report:

```sh
npx --package newman --package newman-reporter-htmlextra -- \
  newman run collections/dtx-integration.postman_collection.json \
  -e environments/partner-shared.postman_environment.json \
  --env-var "apiKey=$YOUR_API_KEY" \
  --reporters cli,htmlextra \
  --reporter-htmlextra-export newman-report.html
```

## How the idempotency check works

`Valid bundle` has a pre-request script that generates a UUID v4 each run and stores it as a collection variable `correlationId`. The request sends that UUID as `X-Correlation-Id` and expects a 201. `Duplicate bundle` reuses the same variable on the next request with the same body, and expects a 409 with a `duplicate` OperationOutcome issue.

This works when running the full collection in order. Running `Duplicate bundle` on its own sends an empty header and falls through to a 400, because the variable is only populated by a prior run of `Valid bundle`.

## What the other requests do

| Request | Demonstrates | Expected response |
| --- | --- | --- |
| No API key | API Gateway auth rejection before the integration runs | 403 |
| Missing X-Correlation-Id | Required header omitted | 400, `required` |
| Invalid X-Correlation-Id | Header present but not a UUID | 400, `value` |
| Empty entry array | Bundle fails `entry.minItems: 1` | 400, `value` |
| Wrong resourceType | Top-level `resourceType` must be `Bundle` | 400, `value` |
| Entry items not objects | Primitives in `entry` fail structural validation | 400, `structure` |
