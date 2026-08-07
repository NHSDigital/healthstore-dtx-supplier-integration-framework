'use strict';

const { execSync, spawn } = require('child_process');
const fs = require('fs');
const net = require('net');
const os = require('os');
const path = require('path');

const ROOT = path.resolve(__dirname, '../../..');
const PRISM = path.join(ROOT, 'node_modules/.bin/prism');

// Real services at 8080/8090. Proxy ports use 4012/4013.
const PORTS = {
  supplier: 8080,
  simulator: 8090,
  healthstoreProxy: 4012, // prism proxy → simulator  (option 2: healthstore-api contract)
  supplierProxy: 4013,    // prism proxy → supplier    (option 1: supplier-api contract)
};

const PID_FILE = path.join(os.tmpdir(), 'integration-pids.json');

// Kill anything left over from a previous run that crashed before teardown ran.
function killStaleProcesses() {
  if (!fs.existsSync(PID_FILE)) return;
  let info;
  try {
    info = JSON.parse(fs.readFileSync(PID_FILE, 'utf8'));
  } catch {
    fs.unlinkSync(PID_FILE);
    return;
  }
  for (const pid of [info.supplierProxy, info.healthstoreProxy, info.simulator, info.supplier]) {
    if (pid) { try { process.kill(pid, 'SIGKILL'); } catch { /* already gone */ } }
  }
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

function spawnLogged(cmd, args, logFile) {
  const proc = spawn(cmd, args, { stdio: ['ignore', 'pipe', 'pipe'] });
  const ws = fs.createWriteStream(logFile);
  proc.stdout.pipe(ws);
  proc.stderr.pipe(ws);
  return proc;
}

module.exports = async function globalSetup() {
  process.stdout.write('\n=== Building ===\n');
  execSync('./gradlew bootJar -q', {
    cwd: path.join(ROOT, 'examples/reference-supplier'),
    stdio: 'inherit',
  });
  execSync('./gradlew bootJar -q', {
    cwd: path.join(ROOT, 'examples/healthstore-simulator'),
    stdio: 'inherit',
  });

  const supplierJar = findJar(path.join(ROOT, 'examples/reference-supplier/build/libs'));
  const simulatorJar = findJar(path.join(ROOT, 'examples/healthstore-simulator/build/libs'));

  const healthstoreProxyLog = path.join(os.tmpdir(), 'prism-healthstore.log');
  const supplierProxyLog = path.join(os.tmpdir(), 'prism-supplier.log');
  fs.writeFileSync(healthstoreProxyLog, '');
  fs.writeFileSync(supplierProxyLog, '');

  killStaleProcesses();
  assertPortsFree(Object.values(PORTS));

  process.stdout.write('\n=== Starting services ===\n');

  const supplier = spawnLogged('java', ['-jar', supplierJar], path.join(os.tmpdir(), 'reference-supplier.log'));

  // Route simulator's outbound supplier calls through the supplier prism proxy.
  const simulator = spawnLogged('java', [
    '-jar', simulatorJar,
    `--simulator.supplier-base-url=http://localhost:${PORTS.supplierProxy}`,
    `--simulator.public-base-url=http://localhost:${PORTS.simulator}`,
  ], path.join(os.tmpdir(), 'healthstore-simulator.log'));

  // No --errors: it would block violating requests with only a generic error
  // (prism drops the field-level [VALIDATOR] detail once it short-circuits to
  // block). Letting requests through and catching violations via the log in
  // global-teardown.js keeps the full diagnostic detail for every violation,
  // including ones on calls the tests don't directly assert on.
  const healthstoreProxy = spawnLogged(PRISM, [
    'proxy',
    path.join(ROOT, 'specification/healthstore-api.yaml'),
    `http://localhost:${PORTS.simulator}`,
    '--port', String(PORTS.healthstoreProxy),
    '--host', '127.0.0.1',
  ], healthstoreProxyLog);

  const supplierProxy = spawnLogged(PRISM, [
    'proxy',
    path.join(ROOT, 'specification/supplier-api.yaml'),
    `http://localhost:${PORTS.supplier}`,
    '--port', String(PORTS.supplierProxy),
    '--host', '127.0.0.1',
  ], supplierProxyLog);

  process.stdout.write('Waiting for all services...\n');
  await Promise.all([
    waitForPort(PORTS.supplier).then(() => process.stdout.write('  reference-supplier ready\n')),
    waitForPort(PORTS.simulator).then(() => process.stdout.write('  healthstore-simulator ready\n')),
    waitForPort(PORTS.healthstoreProxy).then(() => process.stdout.write('  healthstore-proxy ready\n')),
    waitForPort(PORTS.supplierProxy).then(() => process.stdout.write('  supplier-proxy ready\n')),
  ]);

  fs.writeFileSync(PID_FILE, JSON.stringify({
    supplier: supplier.pid,
    simulator: simulator.pid,
    healthstoreProxy: healthstoreProxy.pid,
    supplierProxy: supplierProxy.pid,
    healthstoreProxyLog,
    supplierProxyLog,
  }));
  process.env.INTEGRATION_PID_FILE = PID_FILE;
};
