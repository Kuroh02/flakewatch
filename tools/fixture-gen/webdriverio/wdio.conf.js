export const config = {
  runner: 'local',
  specs: ['./test/specs/**/*.js'],
  maxInstances: 1,
  capabilities: [{
    browserName: 'chrome',
    'goog:chromeOptions': {
      args: ['--headless', '--disable-gpu']
    }
  }],
  logLevel: 'error',
  framework: 'mocha',
  reporters: [
    ['junit', {
      outputDir: './reports',
      outputFileFormat: function(options) {
        return `results.xml`;
      }
    }]
  ],
  mochaOpts: {
    ui: 'bdd',
    timeout: 60000
  }
};
