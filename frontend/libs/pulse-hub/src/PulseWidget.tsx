import { useHubSession } from '@openjcockpit/hub-contract';

/* eslint-disable i18next/no-literal-string -- demo remote; remote translations arrive with the i18n step */
export function PulseWidget() {
  const session = useHubSession();
  return <p data-testid="pulse-widget">Pulse is running for {session.user?.username ?? 'you'}</p>;
}
