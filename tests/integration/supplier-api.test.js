'use strict';

// Calls go through the prism proxy (port 4013) so requests and responses are
// validated against specification/supplier-api.yaml at runtime.
const SUPPLIER_URL = 'http://localhost:4013'; // prism proxy

let token;

beforeAll(async () => {
  const res = await fetch(`${SUPPLIER_URL}/oauth/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: 'grant_type=client_credentials&client_id=healthstore&client_secret=hs-local-secret',
  });
  expect(res.status).toBe(200);
  token = (await res.json()).access_token;
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

  test('400 with invalid credentials', async () => {
    const res = await fetch(`${SUPPLIER_URL}/oauth/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: 'grant_type=client_credentials&client_id=wrong&client_secret=wrong',
    });
    expect(res.status).toBe(400);
  });
});

describe('POST /healthstore-registration-requests', () => {
  test('200 for a process-specific-service-request Task', async () => {
    const res = await fetch(`${SUPPLIER_URL}/healthstore-registration-requests`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'X-Request-ID': crypto.randomUUID(),
        'Content-Type': 'application/fhir+json',
      },
      body: JSON.stringify({
        resourceType: 'Task',
        identifier: [{
          system: 'https://fhir.healthstore.nhs.uk/Id/registration-request',
          value: crypto.randomUUID(),
        }],
        status: 'requested',
        intent: 'order',
        code: {
          coding: [{
            system: 'https://fhir.healthstore.nhs.uk/CodeSystem/task-code',
            code: 'process-specific-service-request',
            display: 'Process specific service request',
          }],
        },
        priority: 'asap',
        authoredOn: new Date().toISOString(),
        focus: {
          identifier: {
            system: 'https://fhir.healthstore.nhs.uk/Id/registration',
            value: crypto.randomUUID(),
          },
        },
      }),
    });
    expect(res.status).toBe(200);
  });

  test('200 for a process-available-service-requests Task', async () => {
    const res = await fetch(`${SUPPLIER_URL}/healthstore-registration-requests`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'X-Request-ID': crypto.randomUUID(),
        'Content-Type': 'application/fhir+json',
      },
      body: JSON.stringify({
        resourceType: 'Task',
        identifier: [{
          system: 'https://fhir.healthstore.nhs.uk/Id/registration-request',
          value: crypto.randomUUID(),
        }],
        status: 'requested',
        intent: 'order',
        code: {
          coding: [{
            system: 'https://fhir.healthstore.nhs.uk/CodeSystem/task-code',
            code: 'process-available-service-requests',
            display: 'Process available service requests',
          }],
        },
        priority: 'routine',
        authoredOn: new Date().toISOString(),
        groupIdentifier: {
          system: 'https://fhir.healthstore.nhs.uk/Id/cohort',
          value: 'integration-test',
        },
      }),
    });
    expect(res.status).toBe(200);
  });
});
