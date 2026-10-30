/**
 * Singletons every federated bundle (host and each remote) must share. A private copy breaks React
 * hooks, router context and the contract's session context, so a remote may not inline any of these.
 * `strictVersion` fails loudly on a version skew instead of silently running two copies.
 */
export const sharedModules = {
  react: { singleton: true, requiredVersion: '^19', strictVersion: true },
  'react-dom': { singleton: true, requiredVersion: '^19', strictVersion: true },
  'react-router': { singleton: true, requiredVersion: '^7', strictVersion: true },
  // Module-level state a remote must not duplicate: the auth token and error translator (api),
  // the query cache and i18n instance (their React contexts), and the shared UI kit.
  '@tanstack/react-query': { singleton: true, requiredVersion: '5.104.5', strictVersion: true },
  i18next: { singleton: true, requiredVersion: '26.5.11', strictVersion: true },
  'react-i18next': { singleton: true, requiredVersion: '17.1.9', strictVersion: true },
  '@openjcockpit/api': { singleton: true, requiredVersion: false },
  '@openjcockpit/i18n': { singleton: true, requiredVersion: false },
  '@openjcockpit/ui': { singleton: true, requiredVersion: false },
  // Workspace lib: shared by name, so HubSessionContext is one object across bundles.
  '@openjcockpit/hub-contract': { singleton: true, requiredVersion: false },
} as const;
