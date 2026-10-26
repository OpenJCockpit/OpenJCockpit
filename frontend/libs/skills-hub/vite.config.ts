import { mergeConfig } from 'vite';
import base from '../../vite.config.base';

// Floors sit just under the measured baseline; raise them as coverage grows.
export default mergeConfig(base, {
  test: {
    coverage: {
      thresholds: { statements: 97, branches: 88, functions: 95, lines: 97 },
    },
  },
});
