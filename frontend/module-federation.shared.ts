/**
 * Singletons every federated bundle (host and each remote) must share. A private copy breaks React
 * hooks, router context and the contract's session context, so a remote may not inline any of these.
 * `strictVersion` fails loudly on a version skew instead of silently running two copies.
 */
export const sharedModules = {
  react: { singleton: true, requiredVersion: '^19', strictVersion: true },
  'react-dom': { singleton: true, requiredVersion: '^19', strictVersion: true },
  'react-router': { singleton: true, requiredVersion: '^7', strictVersion: true },
  // Workspace lib: shared by name, so HubSessionContext is one object across bundles.
  '@openjcockpit/hub-contract': { singleton: true, requiredVersion: false },
} as const;
