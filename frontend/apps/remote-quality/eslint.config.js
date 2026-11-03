import { createConfig } from '../../eslint.config.base.js';

export default [
  ...createConfig(import.meta.dirname),
  {
    // A remote runs in the host's session: it never imports the identity provider.
    files: ['src/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          paths: [
            {
              name: 'keycloak-js',
              message: 'Remotes read the session from @openjcockpit/hub-contract.',
            },
            {
              name: '@openjcockpit/auth',
              message: 'Remotes read the session from @openjcockpit/hub-contract.',
            },
          ],
        },
      ],
    },
  },
];
