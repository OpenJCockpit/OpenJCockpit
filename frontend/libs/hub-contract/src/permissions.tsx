import type { ReactNode } from 'react';
import { Navigate } from 'react-router';
import { isAllowed, useHubSession } from './session';

export const FORBIDDEN_PATH = '/403';

/** The permission (the host's realm role of the same name) that platform administrators hold. */
export const ADMIN_PERMISSION = 'openjcockpit-admin';

/** Any-of permission check for conditional UI. */
export function useCan(...permissions: string[]): boolean {
  return isAllowed(useHubSession(), permissions);
}

export function Can({ permission, children }: { permission: string; children: ReactNode }) {
  return useCan(permission) ? <>{children}</> : null;
}

/** Route element wrapper: sends a user without any of the permissions to the 403 page. */
export function RequirePermission({
  any,
  children,
}: {
  any: readonly string[];
  children: ReactNode;
}) {
  const session = useHubSession();
  return isAllowed(session, any) ? <>{children}</> : <Navigate to={FORBIDDEN_PATH} replace />;
}
