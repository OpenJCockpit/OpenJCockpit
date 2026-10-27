import type { ReactNode } from 'react';
import { Navigate, useSearchParams } from 'react-router';
import { ROUTES } from '../routes';

export function LandingRoute({ children }: { children: ReactNode }) {
  const [params] = useSearchParams();
  // Legacy deep link, kept so existing bookmarks still bypass the hub.
  if (params.get('view') === 'spec-files') return <Navigate to={ROUTES.specFiles} replace />;
  return children;
}
