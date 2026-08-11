'use strict';

const { execSync, spawn } = require('child_process');
const fs = require('fs');
const net = require('net');
const os = require('os');
const path = require('path');

const ROOT = path.resolve(__dirname, '../..');
const PRISM = path.join(ROOT, 'node_modules/.bin/prism');
const PID_FILE = path.join(os.tmpdir(), 'integration-pids.json');

// Real service at 8080. Proxy port uses 4013.
const PORTS = {
  supplier: 8080,
  supplierProxy: 4013, // prism proxy → supplier (supplier-api contract)
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
      throw new Error(`Port ${port} is already in use — stop whatever's running there before running the integration tests.`);
    }
  }
}

function findJar(libsDir) {
  return fs.readdirSync(libsDir)
    .filter(f => f.endsWith('.jar') && !f.includes('plain'))
    .map(f => path.join(libsDir, f))[0];
}

function waitForPort(port, timeoutMs = 90_000) {
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

// Spawns the process and returns everything teardown needs to stop it and
// inspect its output — the single record shared by the PID file.
function startService({ name, port, cmd, args }) {
  const logFile = path.join(os.tmpdir(), `${name}.log`);
  const proc = spawn(cmd, args, { stdio: ['ignore', 'pipe', 'pipe'] });
  const ws = fs.createWriteStream(logFile);
  proc.stdout.pipe(ws);
  proc.stderr.pipe(ws);
  return { name, port, pid: proc.pid, logFile };
}

module.exports = async function globalSetup() {
  process.stdout.write('\n=== Building ===\n');
  execSync('./gradlew bootJar -q', {
    cwd: path.join(ROOT, 'examples/reference-supplier'),
    stdio: 'inherit',
  });

  const supplierJar = findJar(path.join(ROOT, 'examples/reference-supplier/build/libs'));

  killStaleProcesses();
  assertPortsFree(Object.values(PORTS));

  process.stdout.write('\n=== Starting services ===\n');

  // No --errors on the proxies: it would block violating requests with only a
  // generic error (prism drops the field-level [VALIDATOR] detail once it
  // short-circuits to block). Letting requests through and catching violations
  // via the log in global-teardown.js keeps the full diagnostic detail for
  // every violation, including ones on calls the tests don't directly assert on.
  const services = [
    {
      name: 'reference-supplier',
      port: PORTS.supplier,
      cmd: 'java',
      args: ['-jar', supplierJar],
    },
    {
      name: 'supplier-proxy',
      port: PORTS.supplierProxy,
      cmd: PRISM,
      args: [
        'proxy',
        path.join(ROOT, 'specification/supplier-api.yaml'),
        `http://localhost:${PORTS.supplier}`,
        '--port', String(PORTS.supplierProxy),
        '--host', '127.0.0.1',
      ],
    },
  ].map(startService);

  process.stdout.write('Waiting for all services...\n');
  await Promise.all(services.map(({ name, port }) =>
    waitForPort(port).then(() => process.stdout.write(`  ${name} ready\n`))
  ));

  fs.writeFileSync(PID_FILE, JSON.stringify({ services }));
  process.env.INTEGRATION_PID_FILE = PID_FILE;
};
