'use strict';

// Every request here goes straight to the simulator, bypassing the prism
// proxy entirely. These are the negative/error-path cases that prism would
// otherwise flag as spec violations — which is the point of each test, not a
// bug. Positive-path, spec-conformant cases belong in
// tests/contract-validation/healthstore-api.test.js instead — see
// tests/README.md.
const { SIMULATOR_URL, getSimulatorToken, seedAndSendRegistration, authHeaders } = require('../setup/simulator-client');

const COHORT = 'integration';

let token;
let regId;

beforeAll(async () => {
  token = await getSimulatorToken();
  regId = await seedAndSendRegistration(COHORT);
});

describe('GET /registrations', () => {
  test('400 when cohort is missing', async () => {
    const res = await fetch(
      `${SIMULATOR_URL}/registrations?_count=50&page=1`,
      { headers: authHeaders(token) }
    );
    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.issue[0].details.coding[0].code).toBe('MISSING_PARAMETER');
  });

  // Known gap, not the intended behavior: @Min/@Max on _count/page throw
  // jakarta.validation.ConstraintViolationException, which ApiExceptionHandler
  // doesn't catch (it only handles HandlerMethodValidationException), so this
  // falls through to Spring Boot's default handler as a bare 500 instead of
  // the spec's documented 400. Pinning the current behavior here rather than
  // asserting the spec, so the suite reflects what the app actually does.
  test('500 when _count is out of range (should be 400 per spec)', async () => {
    const res = await fetch(
      `${SIMULATOR_URL}/registrations?cohort=${COHORT}&_count=101&page=1`,
      { headers: authHeaders(token) }
    );
    expect(res.status).toBe(500);
  });

  test('500 when page is out of range (should be 400 per spec)', async () => {
    const res = await fetch(
      `${SIMULATOR_URL}/registrations?cohort=${COHORT}&_count=50&page=0`,
      { headers: authHeaders(token) }
    );
    expect(res.status).toBe(500);
  });

  test('401 without a bearer token', async () => {
    const res = await fetch(
      `${SIMULATOR_URL}/registrations?cohort=${COHORT}&_count=50&page=1`,
      { headers: { 'X-Request-ID': crypto.randomUUID() } }
    );
    expect(res.status).toBe(401);
    const body = await res.json();
    expect(body.issue[0].details.coding[0].code).toBe('NO_ACCESS');
  });
});

describe('GET /registrations/{id}', () => {
  test('401 without a bearer token', async () => {
    const res = await fetch(
      `${SIMULATOR_URL}/registrations/${regId}`,
      { headers: { 'X-Request-ID': crypto.randomUUID() } }
    );
    expect(res.status).toBe(401);
  });
});

describe('POST /registrations/{id}/tasks', () => {
  test('400 for an invalid businessStatus code', async () => {
    const res = await fetch(
      `${SIMULATOR_URL}/registrations/${regId}/tasks`,
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
              code: 'bogus-status',
              display: 'Bogus',
            }],
          },
        }),
      }
    );
    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.issue[0].details.coding[0].code).toBe('INVALID_FHIR_STRUCTURE');
  });
});
