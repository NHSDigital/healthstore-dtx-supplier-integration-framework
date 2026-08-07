'use strict';

const fs = require('fs');

function killSilently(pid) {
  try { process.kill(pid, 'SIGTERM'); } catch { /* already gone */ }
}

// Prism outputs NDJSON when not attached to a TTY. Look for entries with a
// `violations` array (spec violations) or level >= 50 (errors).
function extractViolations(name, logFile) {
  if (!fs.existsSync(logFile)) return [];
  const found = [];
  for (const line of fs.readFileSync(logFile, 'utf8').split('\n')) {
    const trimmed = line.trim();
    if (!trimmed) continue;
    try {
      const entry = JSON.parse(trimmed);
      if ((entry.violations && entry.violations.length > 0) || entry.level >= 50) {
        found.push(`[${name}] ${JSON.stringify(entry, null, 2)}`);
      }
    } catch {
      // Pretty-printed fallback: flag lines with prism's [VALIDATOR] prefix.
      if (/\[VALIDATOR\]/.test(trimmed)) found.push(`[${name}] ${trimmed}`);
    }
  }
  return found;
}

module.exports = async function globalTeardown() {
  const pidFile = process.env.INTEGRATION_PID_FILE;
  if (!pidFile || !fs.existsSync(pidFile)) return;

  const info = JSON.parse(fs.readFileSync(pidFile, 'utf8'));

  // Shut down in reverse start order.
  for (const pid of [info.supplierProxy, info.healthstoreProxy, info.simulator, info.supplier]) {
    if (pid) killSilently(pid);
  }

  const violations = [
    ...extractViolations('healthstore-proxy', info.healthstoreProxyLog),
    ...extractViolations('supplier-proxy', info.supplierProxyLog),
  ];

  // Log violations but don't fail — fixing the implementations is for the developer.
  if (violations.length > 0) {
    process.stderr.write(`\n⚠️  Prism detected spec violations (fix the implementations):\n\n${violations.join('\n\n')}\n\n`);
  }
};
