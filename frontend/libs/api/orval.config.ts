import { defineConfig } from 'orval';

// Types only: the hand-written `apiRequest` wrappers stay, so abort signals, `ApiError` and
// server error messages keep working. The no-op client emits no endpoint functions, only the
// schemas into src/generated/contract.ts. Regenerate with `nx run api:generate`; CI
// fails on drift.
export default defineConfig({
  api: {
    input: {
      target: '../../../shared/contracts/openapi.yaml',
      parserOptions: { externalRefs: { allow: ['*'], strategy: 'always' } },
    },
    output: {
      mode: 'single',
      client: () => ({
        client: () => ({ implementation: '', imports: [] }),
        header: () => '',
        dependencies: () => [],
        footer: () => '',
        title: () => '',
      }),
      target: './src/generated/contract.ts',
      clean: true,
      prettier: true,
    },
  },
});
