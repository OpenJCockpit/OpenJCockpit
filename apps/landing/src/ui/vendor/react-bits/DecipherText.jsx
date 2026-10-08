import { useEffect, useRef, useState } from 'react';
import usePrefersReducedMotion from '../../motion/usePrefersReducedMotion.js';

// Adapted from React Bits' `DecryptedText`
// (https://github.com/DavidHDev/react-bits,
// src/content/TextAnimations/DecryptedText/DecryptedText.jsx,
// commit 7a7cf3746f944e14c9ae9b7e2ab899df4ee55c77).
//
// This is NOT a byte-identical vendored copy — it is materially adapted
// (ADR-6: adaptation means ownership). See
// apps/landing/src/ui/vendor/react-bits/ATTRIBUTION.md for the full
// upstream licence text (MIT + Commons Clause License Condition v1.0), the
// non-republication constraint, and the itemised list of the seven
// mandatory local adaptations applied here (dropped `motion/react`,
// `durationMs` API instead of `speed`/`maxIterations`, fixed sr-only leak,
// reduced-motion handling, layout reservation, Arwes' character set,
// simplified single-trigger-on-mount behaviour with full teardown).
//
// The DOM shape (`<Component><span>final text</span><span
// aria-hidden>{visible text}</span></Component>`) is preserved from
// upstream — only the *content* fed to the first span, the trigger
// mechanism, and the CSS used to stack/position the two spans are changed
// (see the `reservedTextStyle`/`scrambleOverlayStyle` comments below for the
// layout-reservation adaptation, #5 in ATTRIBUTION.md).

// Transcribed from Arwes' text package
// (animateTextDecipher/animateTextDecipher.js, read directly out of the
// then-installed node_modules tree, not trusted from the architecture
// document's quotation of it), to preserve the pre-migration visual
// character of the reveal (BR-2).
const CIPHERED_CHARACTERS =
  '    ----____abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';

// The final-text node reserves the box: it is a normal, in-flow grid item
// (so its intrinsic size drives the shared grid cell's track sizing) that is
// made invisible with `opacity: 0` rather than `clip`/`visibility: hidden` —
// opacity does not remove a node from the accessibility tree, so this node
// is still what screen readers announce (see adaptation #3 below), while
// sighted users never see it. Because it is the *only* item that
// participates in track sizing (the scrambling overlay is taken out of flow
// with `position: absolute`), the reserved box tracks the final text's
// metrics only — this holds regardless of whether the font is monospace and
// regardless of whether the scrambling characters share the final text's
// width (adaptation #5 below).
const reservedTextStyle = {
  gridArea: '1 / 1',
  opacity: 0
};

// Stacked in the same grid cell as the reserved text via `grid-area: 1 / 1`,
// but taken out of grid track sizing by `position: absolute` (an
// absolutely-positioned grid item does not contribute to its track's
// content-based sizing) and pinned to fill that cell with `inset: 0`. This
// is what actually overlays the visible scrambling glyphs on top of the
// space the final text reserved, with no dependency on glyph width parity.
const scrambleOverlayStyle = {
  gridArea: '1 / 1',
  position: 'absolute',
  inset: 0
};

const wrapperStyle = {
  display: 'inline-grid',
  position: 'relative',
  whiteSpace: 'pre-wrap'
};

function randomOrder(length) {
  const order = Array.from({ length }, (_, index) => index);
  for (let i = order.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    [order[i], order[j]] = [order[j], order[i]];
  }
  return order;
}

function scramble(text, revealedIndexes, characters) {
  return text
    .split('')
    .map((char, index) => {
      if (char === ' ') {
        return ' ';
      }
      if (revealedIndexes.has(index)) {
        return char;
      }
      return characters[Math.floor(Math.random() * characters.length)];
    })
    .join('');
}

/**
 * Decipher-style reveal of `text`, auto-running once on mount (and again
 * whenever `text` changes), bound to a fixed `durationMs` window rather
 * than an iteration count — required so the reveal cannot drift past the
 * 2.1s `introExit` CSS delay or the 2850ms `onDone` timer at the
 * `IntroGreeting` call site (R-6).
 */
export default function DecipherText({
  as: Component = 'span',
  text,
  durationMs = 1200,
  intervalMs = 40,
  characters = CIPHERED_CHARACTERS,
  className = ''
}) {
  const reducedMotion = usePrefersReducedMotion();
  const [displayText, setDisplayText] = useState(text);
  const intervalRef = useRef(null);

  useEffect(() => {
    window.clearInterval(intervalRef.current);

    if (reducedMotion) {
      setDisplayText(text);
      return undefined;
    }

    const order = randomOrder(text.length);
    const revealed = new Set();
    const startedAt = performance.now();

    setDisplayText(scramble(text, revealed, characters));

    intervalRef.current = window.setInterval(() => {
      const elapsed = performance.now() - startedAt;
      const progress = Math.min(1, durationMs <= 0 ? 1 : elapsed / durationMs);
      const revealCount = Math.round(order.length * progress);

      revealed.clear();
      for (let i = 0; i < revealCount; i += 1) {
        revealed.add(order[i]);
      }

      setDisplayText(scramble(text, revealed, characters));

      if (progress >= 1) {
        window.clearInterval(intervalRef.current);
      }
    }, intervalMs);

    return () => window.clearInterval(intervalRef.current);
  }, [text, durationMs, intervalMs, characters, reducedMotion]);

  return (
    <Component className={className} style={wrapperStyle}>
      {/* Screen readers always get the final string, never a partial
          scramble — this is the upstream defect fix (ATTRIBUTION.md #3).
          This node also reserves the layout box (ATTRIBUTION.md #5): it is
          the only in-flow, size-contributing item in the grid stack. */}
      <span style={reservedTextStyle}>{text}</span>
      <span aria-hidden="true" style={scrambleOverlayStyle}>{displayText}</span>
    </Component>
  );
}
