import { mergeConfig } from 'vite';
import base from '../../vite.config.base';

// Floors sit just under the measured baseline; raise them as coverage grows.
export default mergeConfig(base, {
  test: {
    coverage: {
      thresholds: { statements: 30, branches: 35, functions: 30, lines: 30 },
    },
  },
});
