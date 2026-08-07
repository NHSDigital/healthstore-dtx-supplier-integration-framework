'use strict';

// Calls go through the prism proxy (port 4010) so requests and responses are
// validated against specification/healthstore-api.yaml at runtime.
const SIMULATOR_URL = 'http://localhost:8090'; // direct: control + oauth endpoints not in spec
const HEALTHSTORE_URL = 'http://localhost:4012'; // prism proxy

const COHORT = 'integration-test';

let token;
let regId;

beforeAll(async () => {
  const tokenRes = await fetch(`${SIMULATOR_URL}/oauth2/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: 'grant_type=client_credentials&client_id=dtx-supplier&client_secret=dtx-local-secret',
  });
  expect(tokenRes.status).toBe(200);
  token = (await tokenRes.json()).access_token;

  const seedRes = await fetch(
    `${SIMULATOR_URL}/_simulator/registrations?cohort=${COHORT}&priority=asap`,
    { method: 'POST' }
  );
  expect(seedRes.status).toBe(200);
  regId = (await seedRes.json()).registrationId;

  // This internally calls the supplier via the supplier prism proxy (port 4011).
  const sendRes = await fetch(
    `${SIMULATOR_URL}/_simulator/registrations/${regId}/send`,
    { method: 'POST' }
  );
  expect(sendRes.status).toBe(200);
});

function authHeaders() {
  return {
    Authorization: `Bearer ${token}`,
    'X-Request-ID': crypto.randomUUID(),
  };
}

describe('GET /registrations', () => {
  test('200 for a valid cohort search', async () => {
    const res = await fetch(
      `${HEALTHSTORE_URL}/registrations?cohort=${COHORT}&_count=50&page=1`,
      { headers: authHeaders() }
    );
    expect(res.status).toBe(200);
  });
});

describe('GET /registrations/{id}', () => {
  test('200 for a known registration', async () => {
    const res = await fetch(
      `${HEALTHSTORE_URL}/registrations/${regId}`,
      { headers: authHeaders() }
    );
    expect(res.status).toBe(200);
  });

  test('404 for an unknown registration', async () => {
    const res = await fetch(
      `${HEALTHSTORE_URL}/registrations/00000000-0000-0000-0000-000000000000`,
      { headers: authHeaders() }
    );
    expect(res.status).toBe(404);
  });
});

describe('POST /registrations/{id}/tasks', () => {
  test('200 for a valid lifecycle Task', async () => {
    const res = await fetch(
      `${HEALTHSTORE_URL}/registrations/${regId}/tasks`,
      {
        method: 'POST',
        headers: { ...authHeaders(), 'Content-Type': 'application/fhir+json' },
        body: JSON.stringify({
          resourceType: 'Task',
          status: 'accepted',
          intent: 'order',
          businessStatus: {
            coding: [{
              system: 'https://fhir.healthstore.nhs.uk/CodeSystem/registration-business-status',
              code: 'registered',
              display: 'Registered',
            }],
          },
        }),
      }
    );
    expect(res.status).toBe(200);
  });
});
