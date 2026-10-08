import './ModalShell.scss';
import { useEffect, useRef } from 'react';
import type { ReactNode } from 'react';
import { createPortal } from 'react-dom';

interface ModalShellProps {
  /** id of the element (usually a heading) that names this dialog for assistive technology. */
  labelledBy: string;
  onDismiss: () => void;
  children: ReactNode;
  /** Extra class name(s) for the dialog surface (the inner, role="dialog" element). */
  className?: string;
  /** Extra class name(s) for the full-screen overlay behind the dialog surface. */
  overlayClassName?: string;
}

const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button:not([disabled])',
  'textarea:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(', ');

function focusableElements(container: HTMLElement): HTMLElement[] {
  return Array.from(container.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR));
}

// Stack of currently-mounted dialog surfaces, outermost first. Used so a nested
// ModalShell can mark whatever was open before it as inert — its focus trap must
// stop fighting the new, topmost one. See architecture §7.6 / AC-50.
const openShells: HTMLElement[] = [];

function markInert(el: HTMLElement) {
  el.setAttribute('inert', '');
  // `inert` is not universally supported (and jsdom does not enforce it), so pair
  // it with the aria-hidden fallback assistive technology and testing-library both
  // already understand.
  el.setAttribute('aria-hidden', 'true');
}

function clearInert(el: HTMLElement) {
  el.removeAttribute('inert');
  el.removeAttribute('aria-hidden');
}

/**
 * Shared accessible dialog primitive: role="dialog", focus trap, Escape-to-dismiss,
 * focus restore on close, and inert-parent handling when a second shell opens nested
 * inside a first one (e.g. the comment modal opened from the decision modal).
 */
export function ModalShell({
  labelledBy,
  onDismiss,
  children,
  className,
  overlayClassName,
}: ModalShellProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const onDismissRef = useRef(onDismiss);
  onDismissRef.current = onDismiss;

  // Focus restore: capture whatever had focus before this dialog opened, restore it on close.
  useEffect(() => {
    const previouslyFocused = document.activeElement as HTMLElement | null;
    return () => {
      previouslyFocused?.focus?.();
    };
  }, []);

  // Nested-modal handling: mark whatever shell was previously topmost as inert while
  // this one is open, and clear that the moment this one unmounts.
  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const outer = openShells[openShells.length - 1] ?? null;
    if (outer) markInert(outer);
    openShells.push(el);

    return () => {
      const index = openShells.indexOf(el);
      if (index >= 0) openShells.splice(index, 1);
      if (outer) clearInert(outer);
    };
  }, []);

  // Initial focus: move focus into the dialog, onto its first focusable control.
  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const [first] = focusableElements(el);
    (first ?? el).focus();
  }, []);

  // Escape-to-dismiss and Tab focus-trap: attached imperatively (rather than as a
  // JSX onKeyDown) so the keydown listener lives on the dialog surface without
  // jsx-a11y flagging a non-interactive `role="dialog"` element for having a JSX
  // event handler — the WAI-ARIA APG modal pattern still calls for handling the
  // key here, on the dialog surface itself.
  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        event.stopPropagation();
        onDismissRef.current();
        return;
      }
      if (event.key !== 'Tab') return;
      event.stopPropagation();
      if (!el) return;
      const focusable = focusableElements(el);
      if (focusable.length === 0) {
        event.preventDefault();
        return;
      }
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      const active = document.activeElement as HTMLElement | null;
      const withinDialog = Boolean(active && el.contains(active));

      if (event.shiftKey) {
        if (!withinDialog || active === first) {
          event.preventDefault();
          last.focus();
        }
      } else {
        if (!withinDialog || active === last) {
          event.preventDefault();
          first.focus();
        }
      }
    }

    el.addEventListener('keydown', handleKeyDown);
    return () => el.removeEventListener('keydown', handleKeyDown);
  }, []);

  // Rendered via a portal onto document.body so that nested shells become DOM
  // siblings, never ancestor/descendant — marking an outer shell inert/aria-hidden
  // must never also hide the nested shell stacked on top of it (see AC-50).
  return createPortal(
    <div className={overlayClassName ? `modal-overlay ${overlayClassName}` : 'modal-overlay'}>
      <div
        ref={containerRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={labelledBy}
        tabIndex={-1}
        className={className ? `modal-shell ${className}` : 'modal-shell'}
      >
        {children}
      </div>
    </div>,
    document.body,
  );
}
