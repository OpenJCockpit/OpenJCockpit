/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  css: {
    // Class names in .module.scss files stay kebab/BEM; TSX reads them as camelCase.
    modules: { localsConvention: 'camelCaseOnly' },
    preprocessorOptions: {
      scss: { charset: false },
    },
  },
  server: {
    port: 4000,
    proxy: {
      '/api': 'http://localhost:9080',
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/setupTests.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
  },
});
