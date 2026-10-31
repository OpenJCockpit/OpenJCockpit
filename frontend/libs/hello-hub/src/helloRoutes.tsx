import type { RouteObject } from 'react-router';
import { HelloPage } from './HelloPage';

/** Relative to the app route, which the host mounts as `/hello/*`. */
export const helloRoutes: RouteObject[] = [{ index: true, element: <HelloPage /> }];
