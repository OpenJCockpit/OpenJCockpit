import { Icon, isIconName, type IconName } from '@openjcockpit/ui';

export type SidebarIconName = IconName;

/** The sidebar's settings entry is a tuning-sliders glyph, distinct from the gear used elsewhere. */
const SIDEBAR_GLYPH: Partial<Record<IconName, IconName>> = { settings: 'sliders' };

/** Icon names come from remote apps, so an unknown name falls back to a generic icon. */
export function toSidebarIconName(name: string): SidebarIconName {
  return isIconName(name) ? name : 'app';
}

/** Decorative 20px icon for the sidebar; the label next to it carries the meaning. */
export function SidebarIcon({ name }: { name: SidebarIconName }) {
  return <Icon name={SIDEBAR_GLYPH[name] ?? name} size={20} />;
}
