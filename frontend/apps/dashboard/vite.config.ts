import { federation } from '@module-federation/vite';
import { mergeConfig } from 'vite';
import base from '../../vite.config.base';
import { sharedModules } from '../../module-federation.shared';

// The plugin rewrites the entry for federation, which tests do not need and jsdom cannot run.
const plugins = process.env.VITEST
  ? []
  : [
      federation({
        name: 'dashboard',
        // Remotes are registered at runtime from /federation.manifest.json, never listed here.
        remotes: {},
        shared: sharedModules,
        dts: false,
        manifest: true,
      }),
    ];

export default mergeConfig(base, {
  plugins,
  build: { target: 'esnext' },
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
