'use strict';

const { execSync, spawn } = require('child_process');
const fs = require('fs');
const net = require('net');
const os = require('os');
const path = require('path');

const ROOT = path.resolve(__dirname, '../..');
const PRISM = path.join(ROOT, 'node_modules/.bin/prism');
const SPEC = path.join(ROOT, 'specification/healthstore-api.yaml');
const PID_FILE = path.join(os.tmpdir(), 'integration-pids.json');

// Prism mock of the Registrations API. Nothing implements the platform side
// in this repository, so the mock is the thing under test: it serves the
// spec's examples and validates every request against the spec.
const PORTS = {
  mock: 4013,
};

// Kill anything left over from a previous run that crashed before teardown ran.
function killStaleProcesses() {
  if (!fs.existsSync(PID_FILE)) return;
  try {
    const { services } = JSON.parse(fs.readFileSync(PID_FILE, 'utf8'));
    for (const { pid } of services) { try { process.kill(pid, 'SIGKILL'); } catch { /* already gone */ } }
  } catch { /* corrupt pid file — nothing usable to clean up */ }
  fs.unlinkSync(PID_FILE);
}

// Fail fast with a clear message rather than killing whatever else is bound to the port.
function assertPortsFree(ports) {
  for (const port of ports) {
    let inUse = true;
    try {
      execSync(`lsof -ti:${port}`, { stdio: ['ignore', 'pipe', 'ignore'] });
    } catch {
      inUse = false; // lsof exits non-zero when nothing is listening
    }
    if (inUse) {
      throw new Error(`Port ${port} is already in use — stop whatever's running there before running the contract tests.`);
    }
  }
}

function waitForPort(port, timeoutMs = 60_000) {
  return new Promise((resolve, reject) => {
    const deadline = Date.now() + timeoutMs;
    const attempt = () => {
      const s = net.createConnection({ port, host: '127.0.0.1' });
      s.on('connect', () => { s.destroy(); resolve(); });
      s.on('error', () => {
        if (Date.now() >= deadline) reject(new Error(`Timed out waiting for port ${port}`));
        else setTimeout(attempt, 500);
      });
    };
    attempt();
  });
}

function startService({ name, port, cmd, args }) {
  const logFile = path.join(os.tmpdir(), `${name}.log`);
  const proc = spawn(cmd, args, { stdio: ['ignore', 'pipe', 'pipe'] });
  const ws = fs.createWriteStream(logFile);
  proc.stdout.pipe(ws);
  proc.stderr.pipe(ws);
  return { name, port, pid: proc.pid, logFile };
}

module.exports = async function globalSetup() {
  killStaleProcesses();
  assertPortsFree(Object.values(PORTS));

  process.stdout.write('\n=== Starting Prism mock ===\n');

  const services = [
    {
      name: 'registrations-mock',
      port: PORTS.mock,
      cmd: PRISM,
      args: [
        'mock',
        SPEC,
        '--port', String(PORTS.mock),
        '--host', '127.0.0.1',
      ],
    },
  ].map(startService);

  await Promise.all(services.map(({ name, port }) =>
    waitForPort(port).then(() => process.stdout.write(`  ${name} ready\n`))
  ));

  fs.writeFileSync(PID_FILE, JSON.stringify({ services }));
  process.env.INTEGRATION_PID_FILE = PID_FILE;
};
