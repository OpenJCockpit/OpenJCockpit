import { useHubSession } from '@openjcockpit/hub-contract';

/* eslint-disable i18next/no-literal-string -- proof-of-federation page; remote translations arrive with the i18n step */
/** Shows the host's session instance id: equal ids across remotes prove the contract is shared. */
export function HelloPage() {
  const session = useHubSession();
  return (
    <section>
      <h1>Hello from a federated app</h1>
      <dl>
        <dt>Session instance</dt>
        <dd data-testid="session-id">{session.sessionId}</dd>
        <dt>User</dt>
        <dd data-testid="username">{session.user?.username ?? '-'}</dd>
      </dl>
    </section>
  );
}
