import { federation } from '@module-federation/vite';
import { mergeConfig } from 'vite';
import base from '../../vite.config.base';
import { sharedModules } from '../../module-federation.shared';

const PORT = 4404;

// The plugin rewrites the entry for federation, which tests do not need and jsdom cannot run.
const plugins = process.env.VITEST
  ? []
  : [
      federation({
        name: 'specs',
        filename: 'remoteEntry.js',
        exposes: { './app': './src/app-entry.ts' },
        shared: sharedModules,
        dts: false,
        manifest: true,
      }),
    ];

export default mergeConfig(base, {
  plugins,
  build: { target: 'esnext' },
  server: {
    port: PORT,
    strictPort: true,
    // The host loads this remote from another origin.
    cors: true,
    origin: `http://localhost:${PORT}`,
  },
  preview: { port: PORT, strictPort: true, cors: true },
  test: {
    coverage: {
      // Bootstrap and the federation entry, which only re-exports.
      exclude: ['src/main.tsx', 'src/app-entry.ts'],
      thresholds: { statements: 100, branches: 100, functions: 100, lines: 100 },
    },
  },
});
