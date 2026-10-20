import { useLayoutEffect, useRef, useState } from 'react';
import type { CSSProperties } from 'react';
import { cx } from '../cx';
import styles from './FrameOctagon.module.scss';

// Faithful reimplementation of the octagonal frame previously rendered by
// the Arwes react-frames package's <FrameOctagon />. Geometry is
// transcribed from Arwes' frames package (createFrameOctagonSettings) with
// the defaults that were actually in force in this app: padding = 0, squareSize = 16,
// strokeWidth = 1 (so half-stroke offset so = 0.5), all four corners cut.
// See docs/delivery/arwes-to-react-bits-migration/02-architecture.md §3.2
// (ADR-1) and 03-work-plan.md (FE-1) for the full rationale.

const SQUARE_SIZE = 16;
const STROKE_WIDTH = 1;
const HALF_STROKE = STROKE_WIDTH / 2;

/**
 * Pure function: measured host size -> the three SVG path strings that make
 * up the octagon (one closed "bg" fill, two open "line" strokes that
 * together trace the full perimeter and draw in from opposite corners).
 * Exported standalone so it is trivially unit-testable without mounting a
 * component or a DOM (architecture §11.3).
 */
export function buildFrameOctagonPaths(
  width: number,
  height: number,
): { bg: string; lineA: string; lineB: string } {
  const w = Number.isFinite(width) ? Math.max(0, width) : 0;
  const h = Number.isFinite(height) ? Math.max(0, height) : 0;
  const so = HALF_STROKE;
  const s = SQUARE_SIZE;

  const bg = [
    `M ${so} ${so + s}`,
    `l ${s} ${-s}`,
    `H ${w - so - s}`,
    `l ${s} ${s}`,
    `V ${h - so - s}`,
    `l ${-s} ${s}`,
    `H ${so + s}`,
    `l ${-s} ${-s}`,
    'Z',
  ].join(' ');

  const lineA = [
    `M ${so} ${so + s}`,
    `l ${s} ${-s}`,
    `H ${w - so - s}`,
    `l ${s} ${s}`,
    `V ${h - so - s}`,
  ].join(' ');

  const lineB = [
    `M ${w - so} ${h - so - s}`,
    `l ${-s} ${s}`,
    `H ${so + s}`,
    `l ${-s} ${-s}`,
    `V ${so + s}`,
  ].join(' ');

  return { bg, lineA, lineB };
}

/**
 * Renders an absolutely positioned SVG octagon frame over its parent
 * element (the parent must be `position: relative` and sized, exactly as
 * the pre-migration Arwes usage required). `className` must land on the
 * SVG root so the intro octagon's `drop-shadow` keeps working
 * unchanged (R-12). Callers set `--frame-line-color` /
 * `--frame-bg-color` from their own CSS module via `className`.
 *
 * `pointer-events: none` (in frameOctagon.scss) is an intentional,
 * documented deviation from Arwes, which did not set it — see R-13.
 */
type FrameOctagonProps = {
  className?: string;
  duration?: number;
};

export function FrameOctagon({ className, duration = 1 }: FrameOctagonProps) {
  const svgRef = useRef<SVGSVGElement>(null);
  const [size, setSize] = useState({ width: 0, height: 0 });

  useLayoutEffect(() => {
    const svg = svgRef.current;
    const host = svg ? svg.parentElement : null;
    if (!host) {
      return undefined;
    }

    const measure = () => {
      // Use offsetWidth/offsetHeight (untransformed layout box) so the frame
      // measures correctly even while the host's <Reveal> entrance animation is
      // mid-way through a scale(...) transform. getBoundingClientRect() returns
      // the visually transformed box — smaller during the animation — and because
      // the border-box size itself never changes, ResizeObserver would never
      // trigger a re-measurement to correct it.
      setSize({ width: host.offsetWidth, height: host.offsetHeight });
    };

    // Always take at least one synchronous measurement so the frame renders
    // correctly on first paint even without live resize tracking. Only the
    // *subscription* to further size changes depends on `ResizeObserver`
    // being available — its absence must never mean "no frame at all"
    // (the exact R-1 failure mode this component exists to prevent).
    measure();

    if (typeof ResizeObserver === 'undefined') {
      return undefined;
    }

    const observer = new ResizeObserver(measure);
    observer.observe(host);

    return () => observer.disconnect();
  }, []);

  const { bg, lineA, lineB } = buildFrameOctagonPaths(size.width, size.height);
  const style = { '--frame-duration': `${duration}s` } as CSSProperties;

  return (
    <svg
      ref={svgRef}
      className={cx(styles.frameOctagon, className)}
      // eslint-disable-next-line no-restricted-syntax -- runtime-computed custom property (duration)
      style={style}
      aria-hidden="true"
      role="presentation"
      focusable="false"
    >
      <path className={styles.frameOctagonBg} d={bg} />
      <g className={styles.frameOctagonLines}>
        <path className={styles.frameOctagonLine} d={lineA} pathLength="1" />
        <path className={styles.frameOctagonLine} d={lineB} pathLength="1" />
      </g>
    </svg>
  );
}
