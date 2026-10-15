'use strict';

// Every request here goes to a Prism mock of specification/usage-api.yaml,
// started by tests/setup/global-setup.js on port 4014. Prism validates each
// request against the spec and answers with the spec's own examples, so these
// tests prove that the examples conform to their schemas and that the request
// shapes a supplier will send are accepted. See tests/README.md.

const MOCK_URL = 'http://127.0.0.1:4014';

const CS = 'https://fhir.dtx.national.nhs.uk/CodeSystem';
const ID = 'https://fhir.dtx.national.nhs.uk/Id';
const ODS = 'https://fhir.nhs.uk/Id/ods-organization-code';
const NHS_NUMBER = 'https://fhir.nhs.uk/Id/nhs-number';
const SUBMISSION_ID = '7f3c9a1e-5b2d-4c8e-9f10-6a7b8c9d0e1f';
const BATCH_ID = '2a4c6e8f-1b3d-4f5a-9c8e-7d6b5a4c3e2f';

function headers(extra = {}) {
  return {
    Authorization: 'Bearer mock',
    'X-Request-ID': crypto.randomUUID(),
    ...extra,
  };
}

/** A first (full) report for a HealthStore-route record, as the supplier guide builds it. */
function report({ route = 'HealthStore', registrationId = 'req-0001', nhsNumber = '9990000018' } = {}) {
  const task = {
    resourceType: 'Task',
    status: 'in-progress',
    intent: 'order',
    requester: { identifier: { system: ODS, value: 'QHS1' } },
    businessStatus: { text: 'Open' },
    lastModified: '2026-08-25T10:00:00Z',
    input: [
      {
        type: { text: 'intervention' },
        valueCodeableConcept: { coding: [{ system: `${CS}/intervention-example`, code: 'example', version: '2.3.1' }] },
      },
      { type: { text: 'registration_route' }, valueString: route },
      { type: { text: 'completion_applicable' }, valueBoolean: true },
    ],
    output: [{ type: { text: 'activation_datetime' }, valueDateTime: '2026-08-25T09:30:00Z' }],
  };
  if (route === 'HealthStore') task.basedOn = [{ identifier: { system: `${ID}/registration`, value: registrationId } }];
  return {
    resourceType: 'Bundle',
    type: 'collection',
    entry: [
      { resource: { resourceType: 'Patient', identifier: [{ system: NHS_NUMBER, value: nhsNumber }], birthDate: '1958-03-14' } },
      { resource: task },
    ],
  };
}

async function post(path, body, extraHeaders = {}) {
  return fetch(`${MOCK_URL}${path}`, {
    method: 'POST',
    headers: headers({ 'Content-Type': 'application/fhir+json', ...extraHeaders }),
    body: typeof body === 'string' ? body : JSON.stringify(body),
  });
}

async function get(path, extraHeaders = {}) {
  return fetch(`${MOCK_URL}${path}`, { method: 'GET', headers: headers(extraHeaders) });
}

function issueCodes(outcome) {
  return outcome.issue.map((i) => i.details.coding[0].code);
}

describe('POST /usage-reports', () => {
  test('202 with the Task carrying our id and a status under the status code system', async () => {
    const res = await post('/usage-reports', report());
    expect(res.status).toBe(202);
    expect(res.headers.get('content-type')).toMatch(/application\/fhir\+json/);
    const task = await res.json();
    expect(task).toMatchObject({ resourceType: 'Task', intent: 'order' });
    expect(task.identifier[0].system).toBe(`${ID}/usage-report-submission`);
    expect(task.statusReason.coding[0].system).toBe(`${CS}/usage-report-status`);
    expect(['received', 'on-hold', 'accepted', 'rejected', 'completed']).toContain(task.status);
  });

  test('a self-referral report, with no registration identifier, is a valid request', async () => {
    const res = await post('/usage-reports', report({ route: 'Self-Referral' }));
    expect(res.status).toBe(202);
  });

  test('a body that is not a report draws the 400 with an OperationOutcome under the issue code system', async () => {
    const res = await post('/usage-reports', {});
    expect(res.status).toBe(400);
    const outcome = await res.json();
    expect(outcome.resourceType).toBe('OperationOutcome');
    expect(outcome.issue[0].details.coding[0].system).toBe(`${CS}/usage-report-issue`);
    expect(issueCodes(outcome).length).toBeGreaterThan(0);
  });

  test('a missing X-Request-ID is refused', async () => {
    const res = await fetch(`${MOCK_URL}/usage-reports`, {
      method: 'POST',
      headers: { Authorization: 'Bearer mock', 'Content-Type': 'application/fhir+json' },
      body: JSON.stringify(report()),
    });
    expect(res.status).toBe(400);
  });

  test('no bearer token is 401', async () => {
    const res = await fetch(`${MOCK_URL}/usage-reports`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/fhir+json', 'X-Request-ID': crypto.randomUUID() },
      body: JSON.stringify(report()),
    });
    expect(res.status).toBe(401);
  });
});

describe('GET /submissions/{id}', () => {
  test('200 with the Task, its status and, where judged, a contained OperationOutcome', async () => {
    const res = await get(`/submissions/${SUBMISSION_ID}`);
    expect(res.status).toBe(200);
    const task = await res.json();
    expect(task.resourceType).toBe('Task');
    expect(task.identifier[0].system).toBe(`${ID}/usage-report-submission`);
    if (task.contained) {
      expect(task.contained[0].resourceType).toBe('OperationOutcome');
    }
  });

  test('an unknown id is 404 with an OperationOutcome', async () => {
    const res = await get(`/submissions/${SUBMISSION_ID}`, { Prefer: 'code=404' });
    expect(res.status).toBe(404);
    const outcome = await res.json();
    expect(outcome.resourceType).toBe('OperationOutcome');
    expect(issueCodes(outcome)).toEqual(['REFERENCE_NOT_FOUND']);
  });
});

describe('GET /batches/{id}', () => {
  test('200 with a Bundle of the stored lines, each a Task', async () => {
    const res = await get(`/batches/${BATCH_ID}`);
    expect(res.status).toBe(200);
    const bundle = await res.json();
    expect(bundle.resourceType).toBe('Bundle');
    for (const entry of bundle.entry) expect(entry.resource.resourceType).toBe('Task');
  });
});

describe('GET /eligibility-reference', () => {
  test('200 with plain JSON: the version, your products and their contracting organisations', async () => {
    const res = await get('/eligibility-reference');
    expect(res.status).toBe(200);
    expect(res.headers.get('content-type')).toMatch(/application\/json/);
    const reference = await res.json();
    expect(typeof reference.version).toBe('string');
    expect(Array.isArray(reference.products)).toBe(true);
    expect(Array.isArray(reference.pairs)).toBe(true);
    for (const pair of reference.pairs) {
      expect(pair).toEqual(expect.objectContaining({ commissioner_ods_code: expect.any(String), product_id: expect.any(String), valid_from: expect.any(String) }));
    }
  });
});
