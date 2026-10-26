import { mergeConfig } from 'vite';
import base from '../../vite.config.base';

export default mergeConfig(base, {
  server: {
    port: 4000,
    proxy: {
      '/api': 'http://localhost:9080',
    },
  },
  test: {
    coverage: {
      // Floors sit just under the measured baseline; raise them as coverage grows.
      thresholds: { statements: 90, branches: 90, functions: 84, lines: 91 },
    },
  },
});
