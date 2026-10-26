import { mergeConfig } from 'vite';
import base from '../../vite.config.base';

// Floors sit just under the measured baseline; raise them as coverage grows.
export default mergeConfig(base, {
  test: {
    coverage: {
      // Vendored react-bits components are third-party code, not ours to cover.
      exclude: ['src/vendor/**'],
      thresholds: { statements: 94, branches: 85, functions: 95, lines: 95 },
    },
  },
});
