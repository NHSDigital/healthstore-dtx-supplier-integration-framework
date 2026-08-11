'use strict';

// Every request here goes through the prism proxy, so it's validated against
// specification/supplier-api.yaml at runtime. Deliberately spec-violating
// requests belong in tests/integration/supplier-api.test.js instead — see
// tests/README.md.
const { getSupplierToken, authHeaders, buildSpecificTask, buildAvailableTask } = require('../setup/supplier-client');

const SUPPLIER_URL = 'http://localhost:4013'; // prism proxy

let token;

beforeAll(async () => {
  token = await getSupplierToken(SUPPLIER_URL);
});

describe('POST /oauth/token', () => {
  test('200 with valid client credentials', async () => {
    const res = await fetch(`${SUPPLIER_URL}/oauth/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: 'grant_type=client_credentials&client_id=healthstore&client_secret=hs-local-secret',
    });
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body).toMatchObject({ token_type: 'Bearer' });
    expect(typeof body.access_token).toBe('string');
    expect(typeof body.expires_in).toBe('number');
  });

  test('400 for an unknown client_id', async () => {
    const res = await fetch(`${SUPPLIER_URL}/oauth/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: 'grant_type=client_credentials&client_id=wrong&client_secret=wrong',
    });
    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.error).toBe('invalid_client');
  });

  test('400 for the correct client_id with the wrong client_secret', async () => {
    const res = await fetch(`${SUPPLIER_URL}/oauth/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: 'grant_type=client_credentials&client_id=healthstore&client_secret=wrong',
    });
    expect(res.status).toBe(400);
    const body = await res.json();
    // Same error as an unknown client_id: the reference-supplier doesn't
    // distinguish which credential was wrong, and never actually returns the
    // 401 the spec documents for "Client authentication failed" — every
    // credential failure funnels through invalid_client at 400.
    expect(body.error).toBe('invalid_client');
  });
});

describe('POST /healthstore-registration-requests', () => {
  test('200 for a process-specific-service-request Task', async () => {
    const { task, identifierValue, focusValue, authoredOn } = buildSpecificTask();
    const res = await fetch(`${SUPPLIER_URL}/healthstore-registration-requests`, {
      method: 'POST',
      headers: authHeaders(token),
      body: JSON.stringify(task),
    });
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body).toMatchObject({
      resourceType: 'Task',
      identifier: [{
        system: 'https://fhir.healthstore.nhs.uk/Id/registration-request',
        value: identifierValue,
      }],
      status: 'accepted',
      intent: 'order',
      code: {
        coding: [{ code: 'process-specific-service-request' }],
      },
      priority: 'asap',
      focus: {
        identifier: {
          value: focusValue,
        },
      },
    });
    // Compared by instant, not string: Jackson trims trailing-zero fractional
    // seconds on echo (e.g. ".110" -> ".11"), which is a different string but
    // the same instant.
    expect(new Date(body.authoredOn).getTime()).toBe(new Date(authoredOn).getTime());
    expect(typeof body.lastModified).toBe('string');
  });

  test('200 for a process-available-service-requests Task', async () => {
    const { task, identifierValue, authoredOn } = buildAvailableTask({ cohort: 'contract-validation' });
    const res = await fetch(`${SUPPLIER_URL}/healthstore-registration-requests`, {
      method: 'POST',
      headers: authHeaders(token),
      body: JSON.stringify(task),
    });
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body).toMatchObject({
      resourceType: 'Task',
      identifier: [{
        system: 'https://fhir.healthstore.nhs.uk/Id/registration-request',
        value: identifierValue,
      }],
      status: 'accepted',
      intent: 'order',
      code: {
        coding: [{ code: 'process-available-service-requests' }],
      },
      priority: 'routine',
      groupIdentifier: {
        system: 'https://fhir.healthstore.nhs.uk/Id/cohort',
        value: 'contract-validation',
      },
    });
    expect(new Date(body.authoredOn).getTime()).toBe(new Date(authoredOn).getTime());
    expect(typeof body.lastModified).toBe('string');
  });
});
