import { useHubSession } from '@openjcockpit/hub-contract';

/* eslint-disable i18next/no-literal-string -- demo remote; remote translations arrive with the i18n step */
/** Second remote: its session id must equal the one the first remote shows. */
export function PulsePage() {
  const session = useHubSession();
  return (
    <section>
      <h1>Pulse</h1>
      <dl>
        <dt>Session instance</dt>
        <dd data-testid="session-id">{session.sessionId}</dd>
        <dt>User</dt>
        <dd data-testid="username">{session.user?.username ?? '-'}</dd>
      </dl>
    </section>
  );
}
