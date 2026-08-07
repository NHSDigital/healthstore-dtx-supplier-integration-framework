'use strict';

const SIMULATOR_URL = 'http://localhost:8090';

// /oauth2/token and /_simulator/* are simulator control-plane endpoints, not
// part of specification/healthstore-api.yaml — there's no proxy route for
// them, so both the contract-validation and integration suites hit the
// simulator directly here regardless of which one they otherwise test through.

async function getSimulatorToken() {
  const res = await fetch(`${SIMULATOR_URL}/oauth2/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: 'grant_type=client_credentials&client_id=dtx-supplier&client_secret=dtx-local-secret',
  });
  if (res.status !== 200) throw new Error(`Simulator token exchange failed: ${res.status}`);
  return (await res.json()).access_token;
}

async function seedAndSendRegistration(cohort) {
  const seedRes = await fetch(
    `${SIMULATOR_URL}/_simulator/registrations?cohort=${cohort}&priority=asap`,
    { method: 'POST' }
  );
  if (seedRes.status !== 200) throw new Error(`Seeding a registration failed: ${seedRes.status}`);
  const { registrationId } = await seedRes.json();

  // This internally calls the supplier via the supplier prism proxy (port 4013).
  const sendRes = await fetch(
    `${SIMULATOR_URL}/_simulator/registrations/${registrationId}/send`,
    { method: 'POST' }
  );
  if (sendRes.status !== 200) throw new Error(`Sending the registration failed: ${sendRes.status}`);

  return registrationId;
}

function authHeaders(token) {
  return {
    Authorization: `Bearer ${token}`,
    'X-Request-ID': crypto.randomUUID(),
  };
}

module.exports = { SIMULATOR_URL, getSimulatorToken, seedAndSendRegistration, authHeaders };
