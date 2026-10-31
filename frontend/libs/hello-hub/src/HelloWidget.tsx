import { useHubSession } from '@openjcockpit/hub-contract';

/* eslint-disable i18next/no-literal-string -- proof-of-federation widget; remote translations arrive with the i18n step */
/** Home widget with no data source, to prove a lazily loaded remote component reaches the host. */
export default function HelloWidget() {
  const session = useHubSession();
  return <p data-testid="hello-widget">Hello, {session.user?.username ?? 'guest'}</p>;
}
