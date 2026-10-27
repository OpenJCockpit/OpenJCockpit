import { SiteHeader, type SiteSection } from '../components/SiteHeader/SiteHeader';
import type { ShellProps } from './shell';

export function RouteHeader({ active, shell }: { active: SiteSection; shell: ShellProps }) {
  return <SiteHeader active={active} {...shell} />;
}
