'use strict';

// Every request here goes through the prism proxy, so it's validated against
// specification/healthstore-api.yaml at runtime. Deliberately spec-violating
// requests belong in tests/integration/healthstore-api.test.js instead — see
// tests/README.md.
const { getSimulatorToken, seedAndSendRegistration, authHeaders } = require('../setup/simulator-client');

const HEALTHSTORE_URL = 'http://localhost:4012'; // prism proxy
const COHORT = 'contract-validation';

let token;
let regId;

beforeAll(async () => {
  token = await getSimulatorToken();
  regId = await seedAndSendRegistration(COHORT);
});

describe('GET /registrations', () => {
  test('200 for a valid cohort search', async () => {
    const res = await fetch(
      `${HEALTHSTORE_URL}/registrations?cohort=${COHORT}&_count=50&page=1`,
      { headers: authHeaders(token) }
    );
    expect(res.status).toBe(200);
  });

  test('404 for an unknown cohort', async () => {
    const res = await fetch(
      `${HEALTHSTORE_URL}/registrations?cohort=no-such-cohort&_count=50&page=1`,
      { headers: authHeaders(token) }
    );
    expect(res.status).toBe(404);
    const body = await res.json();
    expect(body.issue[0].details.coding[0].code).toBe('REFERENCE_NOT_FOUND');
  });
});

describe('GET /registrations/{id}', () => {
  test('200 for a known registration', async () => {
    const res = await fetch(
      `${HEALTHSTORE_URL}/registrations/${regId}`,
      { headers: authHeaders(token) }
    );
    expect(res.status).toBe(200);
  });

  test('200 echoes X-Correlation-ID unchanged', async () => {
    const correlationId = crypto.randomUUID();
    const res = await fetch(
      `${HEALTHSTORE_URL}/registrations/${regId}`,
      { headers: { ...authHeaders(token), 'X-Correlation-ID': correlationId } }
    );
    expect(res.status).toBe(200);
    expect(res.headers.get('x-correlation-id')).toBe(correlationId);
  });

  test('404 for an unknown registration', async () => {
    const res = await fetch(
      `${HEALTHSTORE_URL}/registrations/00000000-0000-0000-0000-000000000000`,
      { headers: authHeaders(token) }
    );
    expect(res.status).toBe(404);
  });
});

describe('POST /registrations/{id}/tasks', () => {
  function postTask(id, businessStatusCode, businessStatusDisplay) {
    return fetch(
      `${HEALTHSTORE_URL}/registrations/${id}/tasks`,
      {
        method: 'POST',
        headers: { ...authHeaders(token), 'Content-Type': 'application/fhir+json' },
        body: JSON.stringify({
          resourceType: 'Task',
          status: 'accepted',
          intent: 'order',
          businessStatus: {
            coding: [{
              system: 'https://fhir.healthstore.nhs.uk/CodeSystem/registration-business-status',
              code: businessStatusCode,
              display: businessStatusDisplay,
            }],
          },
        }),
      }
    );
  }

  test.each([
    ['registered', 'Registered'],
    ['rejected', 'Rejected'],
    ['activated', 'Activated'],
    ['deactivated', 'Deactivated'],
  ])('200 for a %s lifecycle Task', async (code, display) => {
    const res = await postTask(regId, code, display);
    expect(res.status).toBe(200);
  });

  test('404 for an unknown registration', async () => {
    const res = await postTask('00000000-0000-0000-0000-000000000000', 'registered', 'Registered');
    expect(res.status).toBe(404);
  });
});
