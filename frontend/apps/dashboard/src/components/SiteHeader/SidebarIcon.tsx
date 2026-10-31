export type SidebarIconName =
  | 'platform'
  | 'workflow'
  | 'specFiles'
  | 'specQueue'
  | 'skillsHub'
  | 'settings'
  | 'hub'
  | 'logout'
  | 'collapse'
  | 'pulse'
  | 'app';

const PATHS: Record<SidebarIconName, string[]> = {
  pulse: ['M3 12h4l3-8 4 16 3-8h4'],
  app: ['M4 4h16v16H4z'],
  platform: ['M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z'],
  workflow: [
    'M6 3.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z',
    'M18 15.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z',
    'M8.5 6H14a3 3 0 0 1 3 3v6.5',
  ],
  specFiles: [
    'M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z',
    'M14 3v5h5',
    'M9 13h6',
    'M9 17h6',
  ],
  specQueue: ['M8 6h13', 'M8 12h13', 'M8 18h13', 'M3 6h.01', 'M3 12h.01', 'M3 18h.01'],
  skillsHub: ['M4 4h6v6H4z', 'M14 4h6v6h-6z', 'M4 14h6v6H4z', 'M14 14h6v6h-6z'],
  settings: ['M4 7h10', 'M18 7h2', 'M4 17h2', 'M10 17h10', 'M16 5v4', 'M8 15v4'],
  hub: ['M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z', 'M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z'],
  logout: ['M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4', 'M16 17l5-5-5-5', 'M21 12H9'],
  collapse: ['M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z', 'M9 4v16'],
};

/** Icon names come from remote apps, so an unknown name falls back to a generic icon. */
export function toSidebarIconName(name: string): SidebarIconName {
  return name in PATHS ? (name as SidebarIconName) : 'app';
}

/** Decorative 20px line icon for the sidebar; the label next to it carries the meaning. */
export function SidebarIcon({ name }: { name: SidebarIconName }) {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {PATHS[name].map((d) => (
        <path key={d} d={d} />
      ))}
    </svg>
  );
}
