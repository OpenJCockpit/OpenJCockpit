import { Suspense, use } from 'react';
import { useRoutes } from 'react-router';
import type { AppDefinition } from '@openjcockpit/hub-contract';
import { useTranslation } from '@openjcockpit/i18n';
import { ErrorBoundary } from '@openjcockpit/ui';
import { ViewLoading } from './ViewLoading';

// One load per app for the page's lifetime; the promise is cached so re-renders do not refetch.
const routeLoads = new WeakMap<AppDefinition, ReturnType<AppDefinition['loadRoutes']>>();

function loadRoutesOnce(app: AppDefinition) {
  let load = routeLoads.get(app);
  if (!load) {
    load = app.loadRoutes();
    routeLoads.set(app, load);
  }
  return load;
}

function RenderRoutes({ app }: { app: AppDefinition }) {
  const routes = use(loadRoutesOnce(app));
  // Mounted under `${route}/*`, so the remote's routes stay relative to its own route.
  return useRoutes(routes);
}

/** Renders a remote's relative route tree inside the host's router. */
export function RemoteRoutes({ app }: { app: AppDefinition }) {
  const { t } = useTranslation();
  return (
    <ErrorBoundary message={t('somethingWrong')} reloadLabel={t('reload')}>
      <Suspense fallback={<ViewLoading />}>
        <RenderRoutes app={app} />
      </Suspense>
    </ErrorBoundary>
  );
}
