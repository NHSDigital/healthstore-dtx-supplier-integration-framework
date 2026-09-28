'use strict';

const fs = require('fs');

function killSilently(pid) {
  try { process.kill(pid, 'SIGTERM'); } catch { /* already gone */ }
}

module.exports = async function globalTeardown() {
  const pidFile = process.env.INTEGRATION_PID_FILE;
  if (!pidFile || !fs.existsSync(pidFile)) return;

  const { services } = JSON.parse(fs.readFileSync(pidFile, 'utf8'));
  for (const { pid } of [...services].reverse()) killSilently(pid);

  fs.unlinkSync(pidFile);
};
