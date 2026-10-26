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
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/setupTests.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/**/*.d.ts', 'src/setupTests.ts', 'src/generated/**'],
      reporter: ['text-summary', 'json-summary', 'lcov'],
      reportsDirectory: './coverage',
    },
  },
});
