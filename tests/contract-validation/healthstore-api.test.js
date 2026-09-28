'use strict';

// Every request here goes to a Prism mock of specification/healthstore-api.yaml.
// Prism validates the request against the spec and answers with the spec's
// own examples, so these tests prove two things: that the examples conform
// to the schemas they are declared under, and that the request shapes a
// supplier will send are accepted. See tests/README.md.

const MOCK_URL = 'http://127.0.0.1:4013';

const REGISTRATION_ID = '7e1f4c8d-2b5a-49e0-8c6f-1d3a9b0e5f27';
const ID_TOKEN = 'eyJhbGciOiJSUzUxMiIsImtpZCI6ImV4YW1wbGUifQ.eyJpc3MiOiJodHRwczovL2F1dGguc2FuZHBpdC5zaWduaW4ubmhzLnVrIn0.c2lnbmF0dXJl';

const ID = 'https://fhir.dtx.national.nhs.uk/Id/registration';
const CS = 'https://fhir.dtx.national.nhs.uk/CodeSystem';
const ODS = 'https://fhir.nhs.uk/Id/ods-organization-code';
const EXT_CONTRACTING_ORG = 'https://fhir.dtx.national.nhs.uk/StructureDefinition/Extension-ContractingOrganisation';

function headers(extra = {}) {
  return {
    Authorization: 'Bearer mock',
    'X-Request-ID': crypto.randomUUID(),
    ...extra,
  };
}

function task({ businessStatus, statusReason, note }) {
  const t = {
    resourceType: 'Task',
    status: businessStatus === 'registered' ? 'accepted' : 'rejected',
    intent: 'order',
    businessStatus: {
      coding: [{ system: `${CS}/registration-business-status`, code: businessStatus }],
    },
  };
  if (statusReason) {
    t.statusReason = { coding: [{ system: `${CS}/acknowledgement-reason`, code: statusReason }] };
  }
  if (note) t.note = [{ text: note }];
  return t;
}

async function retrieve(body, extraHeaders = {}) {
  return fetch(`${MOCK_URL}/registrations/retrieve`, {
    method: 'POST',
    headers: headers({ 'Content-Type': 'application/json', ...extraHeaders }),
    body: JSON.stringify(body),
  });
}

async function acknowledge(body, extraHeaders = {}, id = REGISTRATION_ID) {
  return fetch(`${MOCK_URL}/registrations/${id}/tasks`, {
    method: 'POST',
    headers: headers({ 'Content-Type': 'application/fhir+json', ...extraHeaders }),
    body: JSON.stringify(body),
  });
}

describe('POST /registrations/retrieve', () => {
  test('200 with one open registration carrying the four fields', async () => {
    const res = await retrieve({ id_token: ID_TOKEN }, { Prefer: 'example=one-registration' });
    expect(res.status).toBe(200);
    expect(res.headers.get('content-type')).toMatch(/application\/fhir\+json/);
    const bundle = await res.json();
    expect(bundle).toMatchObject({ resourceType: 'Bundle', type: 'searchset', total: 1 });
    expect(bundle.entry).toHaveLength(1);

    const sr = bundle.entry[0].resource;
    expect(sr).toMatchObject({
      resourceType: 'ServiceRequest',
      meta: { profile: ['https://fhir.hl7.org.uk/StructureDefinition/UKCore-ServiceRequest'] },
      status: 'active',
      intent: 'order',
      subject: { reference: '#patient' },
    });

    // Field 1: the registration identifier.
    expect(sr.identifier).toEqual([{ system: ID, value: REGISTRATION_ID }]);

    // Field 2: one intervention code from the supplier's own catalogue.
    expect(sr.code.coding).toHaveLength(1);
    expect(sr.code.coding[0].system).toMatch(new RegExp(`^${CS}/intervention-[a-z0-9-]+$`));
    expect(typeof sr.code.coding[0].version).toBe('string');
    expect(typeof sr.code.coding[0].code).toBe('string');

    // Field 3: the contracting organisation, one extension carrying its
    // non-empty ODS code and nothing else.
    expect(sr.extension).toHaveLength(1);
    expect(sr.extension[0]).toEqual({
      url: EXT_CONTRACTING_ORG,
      valueIdentifier: { system: ODS, value: expect.stringMatching(/^\S+$/) },
    });

    // Field 4: gender on the contained Patient, the only contained resource.
    expect(sr.contained).toHaveLength(1);
    const patient = sr.contained[0];
    expect(patient).toMatchObject({
      resourceType: 'Patient',
      id: 'patient',
      gender: expect.stringMatching(/^(male|female|other|unknown)$/),
    });

    // Nothing that v0.1 carried and v1.0 removed.
    for (const gone of ['requester', 'performer', 'authoredOn', 'priority', 'reasonCode']) {
      expect(sr).not.toHaveProperty(gone);
    }
    // The contracting organisation is no longer a contained Coverage
    // referenced from insurance.
    expect(sr).not.toHaveProperty('insurance');
    for (const gone of ['identifier', 'name', 'telecom', 'birthDate', 'address', 'generalPractitioner']) {
      expect(patient).not.toHaveProperty(gone);
    }
  });

  test('200 with interim demographics on the contained Patient only', async () => {
    const res = await retrieve({ id_token: ID_TOKEN }, { Prefer: 'example=with-interim-demographics' });
    expect(res.status).toBe(200);
    const bundle = await res.json();
    const patient = bundle.entry[0].resource.contained.find(r => r.resourceType === 'Patient');
    expect(patient.name[0]).toMatchObject({ use: 'official', given: ['Jane'] });
    expect(patient.name[0]).not.toHaveProperty('family');
    expect(patient.telecom.map(t => t.system).sort()).toEqual(['email', 'phone']);
    // Still nothing on the Bundle entry itself beyond the resource.
    expect(Object.keys(bundle.entry[0])).toEqual(['resource']);
  });

  test('200 with an empty Bundle when there are no open registrations', async () => {
    const res = await retrieve({ id_token: ID_TOKEN }, { Prefer: 'example=none' });
    expect(res.status).toBe(200);
    const bundle = await res.json();
    expect(bundle).toMatchObject({ resourceType: 'Bundle', type: 'searchset', total: 0, entry: [] });
  });

  test('401 TOKEN_EXPIRED is an OperationOutcome naming id_token', async () => {
    const res = await retrieve({ id_token: ID_TOKEN }, { Prefer: 'code=401, example=token-expired' });
    expect(res.status).toBe(401);
    const oo = await res.json();
    expect(oo.resourceType).toBe('OperationOutcome');
    expect(oo.issue[0].details.coding[0].code).toBe('TOKEN_EXPIRED');
    expect(oo.issue[0].expression).toContain('id_token');
  });

  test('429 carries Retry-After', async () => {
    const res = await retrieve({ id_token: ID_TOKEN }, { Prefer: 'code=429' });
    expect(res.status).toBe(429);
    expect(Number(res.headers.get('retry-after'))).toBeGreaterThanOrEqual(1);
    const oo = await res.json();
    expect(oo.issue[0].details.coding[0].code).toBe('TOO_MANY_REQUESTS');
  });

  // Prism answers a request that fails spec validation with the operation's
  // own 400 response, so these negative cases also exercise the BadRequest
  // OperationOutcome. The assertion is that the spec rejects the shape.
  test('a body without id_token is rejected by the spec', async () => {
    const res = await retrieve({});
    expect(res.status).toBe(400);
    const oo = await res.json();
    expect(oo.resourceType).toBe('OperationOutcome');
  });

  test('a body with extra fields such as product_id is rejected by the spec', async () => {
    const res = await retrieve({ id_token: ID_TOKEN, product_id: 'x' });
    expect(res.status).toBe(400);
  });

  test('a missing X-Request-ID is rejected by the spec', async () => {
    const res = await fetch(`${MOCK_URL}/registrations/retrieve`, {
      method: 'POST',
      headers: { Authorization: 'Bearer mock', 'Content-Type': 'application/json' },
      body: JSON.stringify({ id_token: ID_TOKEN }),
    });
    expect(res.status).toBe(400);
  });
});

describe('POST /registrations/{registration-id}/tasks', () => {
  test.each([
    ['registered', task({ businessStatus: 'registered' })],
    ['registered, already-registered', task({ businessStatus: 'registered', statusReason: 'already-registered' })],
    ['rejected, licence-exhausted', task({ businessStatus: 'rejected', statusReason: 'licence-exhausted' })],
    ['rejected, intervention-not-configured', task({ businessStatus: 'rejected', statusReason: 'intervention-not-configured' })],
    ['rejected, other with note', task({ businessStatus: 'rejected', statusReason: 'other', note: 'Under 16' })],
  ])('200 with no body for %s', async (_label, body) => {
    const res = await acknowledge(body);
    expect(res.status).toBe(200);
    expect(await res.text()).toBe('');
  });

  test('404 REFERENCE_NOT_FOUND for an unknown registration', async () => {
    const res = await acknowledge(task({ businessStatus: 'registered' }), { Prefer: 'code=404' }, crypto.randomUUID());
    expect(res.status).toBe(404);
    const oo = await res.json();
    expect(oo.issue[0].details.coding[0].code).toBe('REFERENCE_NOT_FOUND');
  });

  test('a v0.1 businessStatus such as activated is rejected by the spec', async () => {
    const res = await acknowledge(task({ businessStatus: 'activated' }));
    expect(res.status).toBe(400);
  });

  test('a v0.1 statusReason such as duplicate is rejected by the spec', async () => {
    const res = await acknowledge(task({ businessStatus: 'rejected', statusReason: 'duplicate' }));
    expect(res.status).toBe(400);
  });

  test('a Task without businessStatus is rejected by the spec', async () => {
    const { businessStatus, ...noStatus } = task({ businessStatus: 'registered' });
    const res = await acknowledge(noStatus);
    expect(res.status).toBe(400);
  });
});
