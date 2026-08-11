/** @type {import('jest').Config} */
module.exports = {
  testMatch: ['<rootDir>/tests/contract-validation/**/*.test.js', '<rootDir>/tests/integration/**/*.test.js'],
  testTimeout: 30_000,
  globalSetup: '<rootDir>/tests/setup/global-setup.js',
  globalTeardown: '<rootDir>/tests/setup/global-teardown.js',
  verbose: true,
};
