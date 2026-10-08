import { useEffect, useRef, useState } from 'react';
import usePrefersReducedMotion from '../motion/usePrefersReducedMotion.js';
import './puffsBackground.scss';

// Clean-room canvas 2D reimplementation of the ambient particle background
// previously rendered by the Arwes react-bgs package's <Puffs />. This is a
// clean-room implementation from the parameter table in architecture
// §2.1/§3.2 (ADR-5 obligation: state which route was taken — this file
// does not read or copy any Arwes source; it reproduces the same
// *documented* algorithm and the same call-site parameters, verified
// independently against Arwes' bgs package (createBackgroundPuffs) during
// design, not by pasting it).
//
// Three guardrails Arwes' implementation did not have (BR-9, performance
// NFR):
//   1. `prefers-reduced-motion: reduce` -> draw one static frame at
//      progress 1 and never start the animation loop.
//   2. `document.visibilitychange` -> cancel the loop when the tab is
//      hidden, resume when visible again.
//   3. `aria-hidden`, `role="presentation"`, `pointer-events: none`, and
//      full teardown of every subscription on unmount so StrictMode's
//      double-invocation of effects is clean.

const outSine = (x) => Math.sin((x * Math.PI) / 2);
const minmaxOverflow01 = (value) => Math.min(1, Math.max(0, value === 1 ? 1 : value % 1));

/** Pure function: create one puff's static parameters for a WxH field. */
export function createPuff(width, height, { padding, xOffset, yOffset, radiusInitial, radiusOffset }) {
  const x = padding + Math.random() * (width - padding * 2);
  const y = padding + Math.random() * (height - padding * 2);
  const xo = xOffset[0] + Math.random() * xOffset[1];
  const yo = yOffset[0] + Math.random() * yOffset[1];
  const ro = radiusOffset[0] + Math.random() * radiusOffset[1];
  return { x, y, r: radiusInitial, xo, yo, ro };
}

/**
 * Pure function: seed `sets` groups of puffs (quantity / sets per group,
 * rounded) for a WxH field. Exported standalone so it is unit-testable by
 * inspection the day a landing test harness is added (O-2).
 */
export function seedPuffFields(width, height, settings) {
  const { quantity, sets } = settings;
  const perSet = Math.round(quantity / sets);
  return Array.from({ length: sets }, () =>
    Array.from({ length: perSet }, () => createPuff(width, height, settings))
  );
}

export default function PuffsBackground({
  color = 'hsl(184 100% 58% / 0.26)',
  quantity = 85,
  sets = 5,
  padding = 0,
  xOffset = [0, 80],
  yOffset = [-10, -90],
  radiusInitial = 4,
  radiusOffset = [1, 9],
  cycleSeconds = 2,
  fadeInSeconds = 1.4,
  className = ''
}) {
  const canvasRef = useRef(null);
  const [visible, setVisible] = useState(false);
  const reducedMotion = usePrefersReducedMotion();

  // Settings are read through a ref inside the effect so that prop
  // identity churn on every parent re-render (e.g. inline array literals
  // at the call site) does not tear down and re-seed the puff field or
  // restart the animation loop — only a genuine reduced-motion change does.
  const settingsRef = useRef(null);
  settingsRef.current = { color, quantity, sets, padding, xOffset, yOffset, radiusInitial, radiusOffset };

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) {
      return undefined;
    }
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      return undefined;
    }

    let disposed = false;
    let puffSets = [];
    let rafId = null;
    let startTimestamp = null;

    const resize = () => {
      const dpr = Math.min(window.devicePixelRatio || 2, 2);
      const { width, height } = canvas.getBoundingClientRect();
      if (canvas.width !== width * dpr || canvas.height !== height * dpr) {
        canvas.width = width * dpr;
        canvas.height = height * dpr;
      }
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.scale(dpr, dpr);
      puffSets = seedPuffFields(width, height, settingsRef.current);
    };

    const drawPuffs = (puffs, progress) => {
      const { color: fillColor } = settingsRef.current;
      ctx.globalAlpha = progress <= 0.5 ? progress * 2 : -2 * progress + 2;
      puffs.forEach((puff) => {
        const x = puff.x + progress * puff.xo;
        const y = puff.y + progress * puff.yo;
        const r = puff.r + progress * puff.ro;
        const gradient = ctx.createRadialGradient(x, y, 0, x, y, r);
        gradient.addColorStop(0, fillColor);
        gradient.addColorStop(1, 'transparent');
        ctx.beginPath();
        ctx.fillStyle = gradient;
        ctx.arc(x, y, r, 0, Math.PI * 2);
        ctx.fill();
        ctx.closePath();
      });
    };

    const draw = (progress) => {
      const { sets: setCount } = settingsRef.current;
      const setOffset = 1 / setCount;
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      puffSets.forEach((puffs, index) => {
        const puffsProgress = minmaxOverflow01(progress + setOffset * index);
        drawPuffs(puffs, outSine(puffsProgress));
      });
    };

    const stopLoop = () => {
      if (rafId !== null) {
        window.cancelAnimationFrame(rafId);
        rafId = null;
      }
      startTimestamp = null;
    };

    const tick = (timestamp) => {
      if (disposed) {
        return;
      }
      if (startTimestamp === null) {
        startTimestamp = timestamp;
      }
      const elapsedSeconds = (timestamp - startTimestamp) / 1000;
      const progress = (elapsedSeconds % cycleSeconds) / cycleSeconds;
      draw(progress);
      rafId = window.requestAnimationFrame(tick);
    };

    const startLoop = () => {
      stopLoop();
      rafId = window.requestAnimationFrame(tick);
    };

    resize();

    if (reducedMotion) {
      draw(1);
    } else {
      startLoop();
    }

    const resizeObserver = new ResizeObserver(() => {
      resize();
      if (reducedMotion) {
        draw(1);
      }
    });
    resizeObserver.observe(canvas);

    const handleVisibilityChange = () => {
      if (document.hidden) {
        stopLoop();
      } else if (!reducedMotion) {
        startLoop();
      }
    };
    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      disposed = true;
      stopLoop();
      resizeObserver.disconnect();
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [reducedMotion, cycleSeconds]);

  useEffect(() => {
    if (reducedMotion) {
      setVisible(true);
      return undefined;
    }
    const rafId = window.requestAnimationFrame(() => setVisible(true));
    return () => window.cancelAnimationFrame(rafId);
  }, [reducedMotion]);

  return (
    <canvas
      ref={canvasRef}
      aria-hidden="true"
      role="presentation"
      className={`puffs-background ${visible ? 'is-visible' : ''} ${className}`.trim()}
      style={{ '--puffs-fade-duration': `${fadeInSeconds}s` }}
    />
  );
}
