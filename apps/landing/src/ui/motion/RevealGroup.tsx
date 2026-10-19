import type { CSSProperties, ElementType, ReactNode } from 'react';

// Stagger scope for a group of <Reveal> descendants (architecture §3.2,
// ADR-4). Sets only the `--reveal-stagger` CSS custom property on a real
// wrapper element — it never assigns indices itself. `Reveal` children
// (however deeply nested through plain, non-participating elements)
// inherit the custom property via normal CSS inheritance and combine it
// with their own explicit `index` to compute their delay.
//
// Renders `as` directly with no extra DOM node beyond what the caller asks
// for, so it can be dropped in as the wrapping element (e.g. the existing
// `<section className="hero">`) without changing the DOM shape.
type RevealGroupProps = {
  as?: ElementType;
  stagger?: number;
  className?: string;
  style?: CSSProperties;
  children?: ReactNode;
  [prop: string]: unknown;
};

export default function RevealGroup({
  as: Component = 'div',
  stagger = 0.16,
  className = '',
  style,
  children,
  ...rest
}: RevealGroupProps) {
  const groupStyle = {
    ...style,
    '--reveal-stagger': `${stagger}s`,
  } as CSSProperties;

  return (
    // eslint-disable-next-line no-restricted-syntax -- runtime-computed custom property (stagger)
    <Component className={className} style={groupStyle} {...rest}>
      {children}
    </Component>
  );
}
