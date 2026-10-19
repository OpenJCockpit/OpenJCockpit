import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const root = fileURLToPath(new URL('..', import.meta.url));

// Same build as the real app, except authentication is stubbed so screenshots
// can be taken without a Keycloak stack. No product code is changed.
export default defineConfig({
  root,
  plugins: [react()],
  css: {
    modules: { localsConvention: 'camelCaseOnly' },
    preprocessorOptions: { scss: { charset: false } },
  },
  resolve: {
    alias: [
      {
        find: /^(\.\.?\/)+auth\/keycloak$/,
        replacement: fileURLToPath(new URL('./keycloak.stub.ts', import.meta.url)),
      },
    ],
  },
  server: { port: 4174, strictPort: true },
});
