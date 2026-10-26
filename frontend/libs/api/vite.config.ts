import { mergeConfig } from 'vite';
import base from '../../vite.config.base';

// Floors sit just under the measured baseline; raise them as coverage grows.
export default mergeConfig(base, {
  test: {
    coverage: {
      // Mock fixtures for other libs' tests are test support, not shipped logic; index.ts is a pure re-export barrel.
      exclude: ['src/testing/**', 'src/index.ts'],
      thresholds: { statements: 100, branches: 100, functions: 100, lines: 100 },
    },
  },
});
