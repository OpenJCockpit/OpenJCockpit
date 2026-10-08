import './Tooltip.scss';
import { Children, cloneElement, useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import type {
  DragEventHandler,
  FocusEventHandler,
  MouseEventHandler,
  ReactElement,
  ReactNode,
  Ref,
} from 'react';
import { createPortal } from 'react-dom';

const GAP = 10; // px between trigger edge and tooltip
const MARGIN = 8; // min px from every viewport edge

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

/**
 * Pure geometry: centers the tooltip over the anchor, flips top/bottom when
 * there is no room, and clamps horizontally to the viewport. Exported so the
 * flip/clamp branches can be unit-tested directly — jsdom does no layout, so
 * driving them only through the component is unreliable.
 */
export function positionTooltip(
  anchor: { top: number; bottom: number; left: number; width: number },
  tip: { width: number; height: number },
  placement: 'top' | 'bottom',
  viewport: { width: number; height: number },
): { top: number; left: number; placement: 'top' | 'bottom' } {
  const centerX = anchor.left + anchor.width / 2;
  const left = clamp(centerX - tip.width / 2, MARGIN, viewport.width - tip.width - MARGIN);

  const above = anchor.top - GAP - tip.height;
  const below = anchor.bottom + GAP;

  let resolved = placement;
  if (placement === 'top' && above < MARGIN) resolved = 'bottom';
  if (placement === 'bottom' && below + tip.height > viewport.height - MARGIN) resolved = 'top';

  return { left, top: resolved === 'top' ? above : below, placement: resolved };
}

function samePosition(
  a: { top: number; left: number; placement: 'top' | 'bottom' } | null,
  b: { top: number; left: number; placement: 'top' | 'bottom' },
): boolean {
  return a !== null && a.top === b.top && a.left === b.left && a.placement === b.placement;
}

/**
 * Composes multiple refs (function refs and/or ref objects) into one
 * callback ref, so injecting our own anchor ref never drops a caller's own
 * ref.
 */
function mergeRefs<T>(...refs: Array<Ref<T> | undefined>): (node: T | null) => void {
  return (node) => {
    for (const ref of refs) {
      if (!ref) continue;
      if (typeof ref === 'function') {
        ref(node);
      } else {
        (ref as { current: T | null }).current = node;
      }
    }
  };
}

type TriggerProps = {
  ref?: Ref<unknown>;
  onMouseEnter?: MouseEventHandler;
  onMouseLeave?: MouseEventHandler;
  onFocus?: FocusEventHandler;
  onBlur?: FocusEventHandler;
  onDragStart?: DragEventHandler;
  'aria-describedby'?: string;
};

export interface TooltipProps {
  /** Floating content. If null/undefined, Tooltip is a pure passthrough — no wrapper behavior, no portal. */
  content: ReactNode;
  /** Preferred side; auto-flips to the opposite when there is no room. Default 'top'. */
  placement?: 'top' | 'bottom';
  /** Exactly one element. Must be a host element (<div>, <button>, …) or a forwardRef
   *  component, so the injected ref resolves to a DOM node. */
  children: ReactElement;
}

/**
 * Wraps a single trigger element; renders its floating content through a
 * `document.body` portal at viewport ('fixed') coordinates so it is never a
 * descendant of a scroll container that would clip it.
 */
export function Tooltip({ content, placement = 'top', children }: TooltipProps) {
  const id = useId();
  const [open, setOpen] = useState(false);
  const [tick, setTick] = useState(0);
  const [pos, setPos] = useState<{ top: number; left: number; placement: 'top' | 'bottom' } | null>(
    null,
  );
  const anchorRef = useRef<HTMLElement | null>(null);
  const tooltipRef = useRef<HTMLDivElement | null>(null);

  useLayoutEffect(() => {
    if (!open || !anchorRef.current || !tooltipRef.current) return;
    const next = positionTooltip(
      anchorRef.current.getBoundingClientRect(),
      tooltipRef.current.getBoundingClientRect(),
      placement,
      { width: window.innerWidth, height: window.innerHeight },
    );
    setPos((prev) => (samePosition(prev, next) ? prev : next));
  }, [open, tick, placement, content]);

  useEffect(() => {
    if (!open) {
      setPos(null);
      return;
    }
    const bump = () => setTick((t) => t + 1);
    const options = { capture: true, passive: true };
    window.addEventListener('scroll', bump, options);
    window.addEventListener('resize', bump, options);
    return () => {
      window.removeEventListener('scroll', bump, options);
      window.removeEventListener('resize', bump, options);
    };
  }, [open]);

  // AD-3: the child's own pre-existing ref lives on the element, not in its
  // props — on react@18.3.1, `child.props.ref` is always undefined and
  // silently merges nothing, while still type-checking under
  // @types/react@19.
  const child = Children.only(children) as ReactElement<TriggerProps> & { ref?: Ref<unknown> };

  if (content == null) {
    return child;
  }

  const existingProps = child.props;
  const describedBy = open
    ? [existingProps['aria-describedby'], id].filter(Boolean).join(' ')
    : existingProps['aria-describedby'];

  const cloned = cloneElement(child, {
    ref: mergeRefs<HTMLElement>(anchorRef, child.ref as Ref<HTMLElement> | undefined),
    onMouseEnter: (e) => {
      existingProps.onMouseEnter?.(e);
      setOpen(true);
    },
    onMouseLeave: (e) => {
      existingProps.onMouseLeave?.(e);
      setOpen(false);
    },
    onFocus: (e) => {
      existingProps.onFocus?.(e);
      setOpen(true);
    },
    onBlur: (e) => {
      existingProps.onBlur?.(e);
      setOpen(false);
    },
    onDragStart: (e) => {
      existingProps.onDragStart?.(e);
      setOpen(false);
    },
    'aria-describedby': describedBy,
  });

  return (
    <>
      {cloned}
      {open &&
        createPortal(
          // Portal onto document.body — same pattern as ModalShell.tsx —
          // so this floating box is never a descendant of a scroll
          // container that would clip it (the bug this component fixes).
          <div
            ref={tooltipRef}
            id={id}
            role="tooltip"
            className="tooltip"
            style={{
              top: pos?.top ?? 0,
              left: pos?.left ?? 0,
              visibility: pos ? 'visible' : 'hidden',
            }}
          >
            {content}
          </div>,
          document.body,
        )}
    </>
  );
}
