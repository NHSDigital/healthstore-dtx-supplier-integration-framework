'use strict';

// Pulls the FHIR resources out of the spec's examples so the HL7 FHIR
// validator can check them against base R4 and the UK Core profiles.
// Usage: node tests/fhir/extract-examples.js <out-dir>

const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');

const ROOT = path.resolve(__dirname, '../..');
const ENDPOINTS = path.join(ROOT, 'specification/components/endpoints');
const outDir = process.argv[2] || path.join(ROOT, 'build/fhir-examples');
fs.mkdirSync(outDir, { recursive: true });

const load = f => yaml.safeLoad(fs.readFileSync(path.join(ENDPOINTS, f), 'utf8'));
const write = (name, resource) => {
  const file = path.join(outDir, `${name}.json`);
  fs.writeFileSync(file, JSON.stringify(resource, null, 2));
  process.stdout.write(`${file}\n`);
};

// Every ServiceRequest inside every retrieve response example.
const retrieve = load('postRetrieveRegistrations.yaml');
const bundles = retrieve.post.responses['200'].content['application/fhir+json'].examples;
for (const [name, { value }] of Object.entries(bundles)) {
  (value.entry || []).forEach((e, i) => write(`servicerequest-${name}-${i}`, e.resource));
}

// Every Task in the acknowledgement request examples.
const tasks = load('postRegistrationTask.yaml');
const bodies = tasks.post.requestBody.content['application/fhir+json'].examples;
for (const [name, { value }] of Object.entries(bodies)) write(`task-${name}`, value);
