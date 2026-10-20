import type { CSSProperties, ElementType, ReactNode } from 'react';
import { cx } from '../cx';
import styles from './Reveal.module.scss';

// Entrance-animation primitive replacing the Arwes react-animated
// package's <Animated /> for this app's six entrance profiles (auth card, topbar, intro frame,
// hero copy, two portal orbs). CSS-only by design (ADR-3): no WAAPI, no JS
// timers. This makes the existing global
// `@media (prefers-reduced-motion: reduce)` block in styles/_reset.scss
// automatically authoritative for every entrance animation in the app.
//
// Stagger indices are always passed explicitly by the caller via `index`
// and are never auto-incremented during render (ADR-4) — an auto-increment
// counter would be a render-time side effect, which React's rendering model
// disallows regardless of whether StrictMode is enabled. (StrictMode itself
// is currently reverted in main.tsx for an unrelated, documented reason —
// the Keycloak singleton double-init issue, ADR-8 — so this rule is
// followed on its own merits here, not because StrictMode is catching
// violations at runtime.)
//
// All props other than the ones listed below pass straight through to the
// rendered element, so e.g. `type="button"` and `onClick` on the portal
// orbs keep working with no extra wrapper element — the DOM shape stays
// identical to the pre-migration `<Animated as="button">` output, plus one
// additional `reveal` class used purely as the CSS animation hook.
type RevealProps = {
  as?: ElementType;
  duration?: number;
  index?: number;
  from?: { y?: number; scale?: number };
  className?: string;
  style?: CSSProperties;
  children?: ReactNode;
  // Remaining props (type, onClick, data-*, aria-*) pass through to the rendered element.
  [prop: string]: unknown;
};

export function Reveal({
  as: Component = 'div',
  duration = 1,
  index = 0,
  from = {},
  className,
  style,
  children,
  ...rest
}: RevealProps) {
  const { y, scale } = from;

  const revealStyle = {
    ...style,
    '--reveal-duration': `${duration}s`,
    '--reveal-index': index,
  } as CSSProperties & Record<`--${string}`, string | number>;

  if (y !== undefined) {
    revealStyle['--reveal-y-from'] = `${y}px`;
  }
  if (scale !== undefined) {
    revealStyle['--reveal-scale-from'] = scale;
  }

  return (
    // eslint-disable-next-line no-restricted-syntax -- runtime-computed custom properties (duration, index, from)
    <Component className={cx(styles.reveal, className)} style={revealStyle} {...rest}>
      {children}
    </Component>
  );
}
