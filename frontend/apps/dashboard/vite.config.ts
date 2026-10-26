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
      // Bootstrap and test helpers, not logic worth covering.
      exclude: ['src/main.tsx', 'src/testUtils/**'],
      // Floors sit at the measured baseline; the two remaining branches are unreachable defensive code.
      thresholds: { statements: 100, branches: 100, functions: 100, lines: 100 },
    },
  },
});
