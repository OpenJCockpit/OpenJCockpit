import { createConfig } from '../../eslint.config.base.js';

export default [
  ...createConfig(import.meta.dirname),

  // Every backend call goes through apiRequest (core/http.ts), which attaches the token and
  // renews an expired session once on a 401. A raw fetch would silently skip both.
  {
    files: ['src/**/*.ts'],
    ignores: ['src/core/http.ts', 'src/**/*.test.ts'],
    rules: {
      'no-restricted-globals': [
        'error',
        {
          name: 'fetch',
          message:
            'Use apiRequest / apiRequestVoid from core/http so the 401 refresh-and-retry applies.',
        },
      ],
    },
  },
];
