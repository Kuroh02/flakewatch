// @ts-check
const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './tests',
  retries: 1,
  reporter: [
    ['junit', { outputFile: 'reports/results.xml' }]
  ],
});
