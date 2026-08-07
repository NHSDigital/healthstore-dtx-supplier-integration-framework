/** @type {import('jest').Config} */
module.exports = {
  testMatch: ['<rootDir>/tests/integration/**/*.test.js'],
  testTimeout: 30_000,
  globalSetup: '<rootDir>/tests/integration/setup/global-setup.js',
  globalTeardown: '<rootDir>/tests/integration/setup/global-teardown.js',
  verbose: true,
};
