import type { RouteObject } from 'react-router';
import { PulsePage } from './PulsePage';

/** Relative to the app route, which the host mounts as `/pulse/*`. */
export const pulseRoutes: RouteObject[] = [{ index: true, element: <PulsePage /> }];
