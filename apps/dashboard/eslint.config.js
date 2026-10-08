import { defineConfig, globalIgnores } from 'eslint/config';
import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import reactHooks from 'eslint-plugin-react-hooks';
import eslintComments from '@eslint-community/eslint-plugin-eslint-comments';
import prettierConfig from 'eslint-config-prettier/flat';
import globals from 'globals';

export default defineConfig(
  // 1. Global ignores
  globalIgnores([
    'dist/**',
    'node_modules/**',
    'coverage/**',
    'public/**',
    'docs/**',
    'eslint-suppressions.json',
  ]),

  // 2. Stale eslint-disable directives become errors
  {
    linterOptions: {
      reportUnusedDisableDirectives: 'error',
    },
  },

  // 3. Require a justification comment on every eslint-disable directive
  {
    files: ['**/*.{ts,tsx,js}'],
    plugins: {
      '@eslint-community/eslint-comments': eslintComments,
    },
    rules: {
      '@eslint-community/eslint-comments/require-description': 'error',
    },
  },

  // 4. Core + TypeScript correctness baseline
  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, tseslint.configs.recommended],
  },

  // 5. Type-aware linting, scoped via tsconfig.eslint.json
  {
    files: ['src/**/*.{ts,tsx}', 'vite.config.ts'],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.eslint.json'],
        tsconfigRootDir: import.meta.dirname,
      },
    },
    rules: {
      '@typescript-eslint/await-thenable': 'error',
      '@typescript-eslint/no-floating-promises': 'error',
      '@typescript-eslint/no-misused-promises': 'error',
      '@typescript-eslint/no-unnecessary-type-assertion': 'error',
    },
  },

  // 6. Accessibility rules — must re-assert parserOptions.project / ecmaFeatures.jsx,
  // because jsx-a11y's flatConfigBase would otherwise clobber block 5's type-aware setup.
  {
    files: ['src/**/*.{ts,tsx}'],
    ...jsxA11y.flatConfigs.recommended,
    languageOptions: {
      ...jsxA11y.flatConfigs.recommended.languageOptions,
      globals: {
        ...globals.browser,
      },
      parserOptions: {
        project: ['./tsconfig.eslint.json'],
        tsconfigRootDir: import.meta.dirname,
        ecmaFeatures: { jsx: true },
      },
    },
  },

  // 7. React Hooks correctness
  {
    files: ['src/**/*.{ts,tsx}'],
    ...reactHooks.configs['recommended-latest'],
  },

  // 8. Project policy rules
  {
    files: ['src/**/*.{ts,tsx}'],
    rules: {
      'no-console': ['error', { allow: ['warn', 'error'] }],
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
    },
  },

  // Vitest globals for test files (forward-looking; all current tests import explicitly)
  {
    files: ['src/**/*.test.{ts,tsx}', 'src/setupTests.ts'],
    languageOptions: {
      globals: {
        describe: 'readonly',
        it: 'readonly',
        test: 'readonly',
        expect: 'readonly',
        vi: 'readonly',
        beforeEach: 'readonly',
        afterEach: 'readonly',
        beforeAll: 'readonly',
        afterAll: 'readonly',
      },
    },
  },

  // 9. The config file lints itself, non-type-aware
  {
    files: ['eslint.config.js'],
    extends: [js.configs.recommended],
    languageOptions: {
      globals: globals.node,
    },
  },

  // 10. Prettier compatibility — must be last so it wins over every earlier block
  prettierConfig,

  // 11. `curly` must come after block 10: eslint-config-prettier disables it by default
  // (it's one of the rules that only some options of are Prettier-safe). 'multi-line'
  // requires braces once an if/else/for/while body doesn't fit on the same line as its
  // header — Prettier itself never collapses such a body back onto one line, so there is
  // no fight between the two tools.
  {
    files: ['src/**/*.{ts,tsx}'],
    rules: {
      curly: ['error', 'multi-line'],
    },
  },
);
