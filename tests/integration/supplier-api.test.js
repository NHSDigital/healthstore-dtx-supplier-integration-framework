'use strict';

// Every request here goes straight to the reference-supplier, bypassing the
// prism proxy entirely. These are the negative/error-path cases that prism
// would otherwise flag as spec violations — which is the point of each test,
// not a bug. Positive-path, spec-conformant cases belong in
// tests/contract-validation/supplier-api.test.js instead — see
// tests/README.md.
const { getSupplierToken, authHeaders, buildSpecificTask, buildAvailableTask } = require('../setup/supplier-client');

const SUPPLIER_URL = 'http://localhost:8080'; // direct: reference-supplier

let token;

beforeAll(async () => {
  token = await getSupplierToken(SUPPLIER_URL);
});

describe('POST /oauth/token', () => {
  test('400 for an unsupported grant_type', async () => {
    const res = await fetch(`${SUPPLIER_URL}/oauth/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: 'grant_type=password&client_id=healthstore&client_secret=hs-local-secret',
    });
    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.error).toBe('unsupported_grant_type');
  });

  test('400 when a required field is missing', async () => {
    const res = await fetch(`${SUPPLIER_URL}/oauth/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: 'grant_type=client_credentials&client_id=healthstore',
    });
    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.error).toBe('invalid_request');
  });
});

describe('POST /healthstore-registration-requests', () => {
  test('401 without a bearer token', async () => {
    const res = await fetch(`${SUPPLIER_URL}/healthstore-registration-requests`, {
      method: 'POST',
      headers: {
        'X-Request-ID': crypto.randomUUID(),
        'Content-Type': 'application/fhir+json',
      },
      body: JSON.stringify({ resourceType: 'Task' }),
    });
    expect(res.status).toBe(401);
    const body = await res.json();
    expect(body.issue[0].details.coding[0].code).toBe('NO_ACCESS');
  });

  test('400 when a Task has both focus and groupIdentifier', async () => {
    const { task } = buildSpecificTask({
      groupIdentifier: {
        system: 'https://fhir.healthstore.nhs.uk/Id/cohort',
        value: 'integration',
      },
    });
    const res = await fetch(`${SUPPLIER_URL}/healthstore-registration-requests`, {
      method: 'POST',
      headers: authHeaders(token),
      body: JSON.stringify(task),
    });
    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.issue[0].details.coding[0].code).toBe('INVALID_FHIR_STRUCTURE');
  });

  test('400 when a Task has neither focus nor groupIdentifier', async () => {
    const { task } = buildAvailableTask({ groupIdentifier: undefined });
    const res = await fetch(`${SUPPLIER_URL}/healthstore-registration-requests`, {
      method: 'POST',
      headers: authHeaders(token),
      body: JSON.stringify(task),
    });
    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.issue[0].details.coding[0].code).toBe('INVALID_FHIR_STRUCTURE');
  });

  test('400 when X-Request-ID is missing', async () => {
    const { task } = buildAvailableTask({ cohort: 'integration' });
    const res = await fetch(`${SUPPLIER_URL}/healthstore-registration-requests`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/fhir+json',
      },
      body: JSON.stringify(task),
    });
    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.issue[0].details.coding[0].code).toBe('MISSING_HEADER');
  });

  test('400 when a required field is missing from an otherwise valid Task', async () => {
    // priority omitted — a valid oneOf match (code + focus agree), but
    // missing a field required once that subtype is resolved.
    const { task } = buildSpecificTask({ priority: undefined });
    const res = await fetch(`${SUPPLIER_URL}/healthstore-registration-requests`, {
      method: 'POST',
      headers: authHeaders(token),
      body: JSON.stringify(task),
    });
    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.issue[0].details.coding[0].code).toBe('INVALID_VALUE');
    expect(body.issue[0].expression).toContain('priority');
  });
});
