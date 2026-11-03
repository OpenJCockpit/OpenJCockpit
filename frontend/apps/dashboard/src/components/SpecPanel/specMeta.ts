import type { SpecFile } from '@openjcockpit/api';

const HEADING_PREFIX = '# ';

/** The spec's title: its first top-level markdown heading, else a readable form of the file name. */
export function specTitle(spec: Pick<SpecFile, 'fileName' | 'content'>): string {
  const heading = spec.content
    ?.split('\n')
    .map((line) => line.trim())
    .find((line) => line.startsWith(HEADING_PREFIX));
  if (heading) return heading.slice(HEADING_PREFIX.length).trim();

  const base = spec.fileName.slice(spec.fileName.lastIndexOf('/') + 1);
  const dot = base.lastIndexOf('.');
  const stem = (dot > 0 ? base.slice(0, dot) : base).split('-').join(' ').split('_').join(' ');
  return stem.charAt(0).toUpperCase() + stem.slice(1);
}

const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['year', 365 * 24 * 3600],
  ['month', 30 * 24 * 3600],
  ['day', 24 * 3600],
  ['hour', 3600],
  ['minute', 60],
];

/** "2 hours ago" in the active locale; unparseable values are returned as received. */
export function formatRelativeTime(value: string, locale: string, now = Date.now()): string {
  const time = new Date(value).getTime();
  if (Number.isNaN(time)) return value;
  const seconds = Math.round((time - now) / 1000);
  const formatter = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' });
  const unit = UNITS.find(([, size]) => Math.abs(seconds) >= size);
  if (!unit) return formatter.format(0, 'minute');
  return formatter.format(Math.round(seconds / unit[1]), unit[0]);
}
