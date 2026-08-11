'use strict';

const fs = require('fs');

function killSilently(pid) {
  try { process.kill(pid, 'SIGTERM'); } catch { /* already gone */ }
}

// prism-cli always pretty-prints its logs via signale (never NDJSON, TTY or not).
// In proxy mode it only logs a [VALIDATOR] line when a request/response actually
// violates the spec (nothing is logged for a clean one), so this is safe as a
// pure violation filter — one line per broken schema rule.
function extractViolations(name, logFile) {
  if (!fs.existsSync(logFile)) return [];
  return fs.readFileSync(logFile, 'utf8')
    .split('\n')
    .filter(line => /\[VALIDATOR\]/.test(line))
    .map(line => `[${name}] ${line.trim()}`);
}

module.exports = async function globalTeardown() {
  const pidFile = process.env.INTEGRATION_PID_FILE;
  if (!pidFile || !fs.existsSync(pidFile)) return;

  const { services } = JSON.parse(fs.readFileSync(pidFile, 'utf8'));

  // Shut down in reverse start order.
  for (const { pid } of [...services].reverse()) killSilently(pid);

  // Requests aren't blocked at the proxy (see global-setup.js), so this log scrape
  // is the only thing that catches a spec violation.
  const violations = services
    .filter(({ name }) => name.endsWith('-proxy'))
    .flatMap(({ name, logFile }) => extractViolations(name, logFile));

  if (violations.length > 0) {
    process.stderr.write(`\n⚠️  Prism detected spec violations:\n\n${violations.join('\n\n')}\n\n`);
    process.exitCode = 1;
  }

  fs.unlinkSync(pidFile);
};
