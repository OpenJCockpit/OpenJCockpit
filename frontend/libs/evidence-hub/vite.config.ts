import { mergeConfig } from 'vite';
import base from '../../vite.config.base';

// Floors sit just under the measured baseline; raise them as coverage grows.
export default mergeConfig(base, {
  test: {
    coverage: {
      thresholds: { statements: 100, branches: 100, functions: 100, lines: 100 },
    },
  },
});
