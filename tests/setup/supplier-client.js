'use strict';

async function getSupplierToken(baseUrl) {
  const res = await fetch(`${baseUrl}/oauth/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: 'grant_type=client_credentials&client_id=healthstore&client_secret=hs-local-secret',
  });
  if (res.status !== 200) throw new Error(`Supplier token exchange failed: ${res.status}`);
  return (await res.json()).access_token;
}

function authHeaders(token) {
  return {
    Authorization: `Bearer ${token}`,
    'X-Request-ID': crypto.randomUUID(),
    'Content-Type': 'application/fhir+json',
  };
}

// Builders for the two RegistrationRequestTask shapes accepted by
// POST /healthstore-registration-requests. Named values (identifierValue,
// focusValue, authoredOn) are returned alongside the task so a test can
// assert the response echoes them back unchanged. Any other property in
// `overrides` is merged onto the built task as-is — set a key to `undefined`
// to delete a field that's normally present (JSON.stringify drops it), or add
// a key that isn't normally there — which is how the negative-path tests in
// tests/integration/supplier-api.test.js build a Task that violates the spec.

function buildSpecificTask({
  identifierValue = crypto.randomUUID(),
  focusValue = crypto.randomUUID(),
  authoredOn = new Date().toISOString(),
  ...overrides
} = {}) {
  const task = {
    resourceType: 'Task',
    identifier: [{
      system: 'https://fhir.healthstore.nhs.uk/Id/registration-request',
      value: identifierValue,
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
    authoredOn,
    focus: {
      identifier: {
        system: 'https://fhir.healthstore.nhs.uk/Id/registration',
        value: focusValue,
      },
    },
    ...overrides,
  };
  return { task, identifierValue, focusValue, authoredOn };
}

function buildAvailableTask({
  identifierValue = crypto.randomUUID(),
  authoredOn = new Date().toISOString(),
  cohort = 'integration-test',
  ...overrides
} = {}) {
  const task = {
    resourceType: 'Task',
    identifier: [{
      system: 'https://fhir.healthstore.nhs.uk/Id/registration-request',
      value: identifierValue,
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
    authoredOn,
    groupIdentifier: {
      system: 'https://fhir.healthstore.nhs.uk/Id/cohort',
      value: cohort,
    },
    ...overrides,
  };
  return { task, identifierValue, authoredOn, cohort };
}

module.exports = { getSupplierToken, authHeaders, buildSpecificTask, buildAvailableTask };
