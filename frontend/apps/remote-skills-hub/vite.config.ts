import { federation } from '@module-federation/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';
import { sharedModules } from '../../module-federation.shared';

const PORT = 4403;

export default defineConfig({
  plugins: [
    react(),
    federation({
      name: 'skillsHub',
      filename: 'remoteEntry.js',
      exposes: { './app': './src/app-entry.ts' },
      shared: sharedModules,
      dts: false,
      manifest: true,
    }),
  ],
  build: { target: 'esnext' },
  server: {
    port: PORT,
    strictPort: true,
    // The host loads this remote from another origin.
    cors: true,
    origin: `http://localhost:${PORT}`,
  },
  preview: { port: PORT, strictPort: true, cors: true },
});
