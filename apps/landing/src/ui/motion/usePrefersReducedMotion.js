import { useEffect, useState } from 'react';

// Shared reduced-motion primitive for the two JS/canvas-driven components in
// this tree (PuffsBackground, DecipherText). Entrance animations are
// CSS-only (see Reveal.jsx / reveal.css) and are covered for free by the
// existing global `@media (prefers-reduced-motion: reduce)` block in
// styles.css — this hook exists specifically for the two components that
// block can't reach (BR-9, AC-11).

const QUERY = '(prefers-reduced-motion: reduce)';

export function getPrefersReducedMotion() {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return false;
  }
  return window.matchMedia(QUERY).matches;
}

export default function usePrefersReducedMotion() {
  const [reduced, setReduced] = useState(getPrefersReducedMotion);

  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return undefined;
    }

    const mediaQueryList = window.matchMedia(QUERY);
    const handleChange = (event) => setReduced(event.matches);

    if (typeof mediaQueryList.addEventListener === 'function') {
      mediaQueryList.addEventListener('change', handleChange);
      return () => mediaQueryList.removeEventListener('change', handleChange);
    }

    // Safari < 14 fallback.
    mediaQueryList.addListener(handleChange);
    return () => mediaQueryList.removeListener(handleChange);
  }, []);

  return reduced;
}
