import { act, render } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PuffsBackground, createPuff, seedPuffFields } from './PuffsBackground';

const settings = {
  padding: 10,
  xOffset: [0, 80] as [number, number],
  yOffset: [-10, -90] as [number, number],
  radiusInitial: 4,
  radiusOffset: [1, 9] as [number, number],
};

describe('createPuff', () => {
  afterEach(() => vi.restoreAllMocks());

  it('places the puff inside the padded field with the initial radius', () => {
    vi.spyOn(Math, 'random').mockReturnValue(0);
    expect(createPuff(100, 50, settings)).toEqual({ x: 10, y: 10, r: 4, xo: 0, yo: -10, ro: 1 });
    vi.spyOn(Math, 'random').mockReturnValue(1);
    expect(createPuff(100, 50, settings)).toEqual({ x: 90, y: 40, r: 4, xo: 80, yo: -100, ro: 10 });
  });
});

describe('seedPuffFields', () => {
  it('creates `sets` groups of quantity / sets puffs, rounded', () => {
    const fields = seedPuffFields(100, 100, { ...settings, quantity: 10, sets: 3 });
    expect(fields).toHaveLength(3);
    for (const set of fields) expect(set).toHaveLength(3);
  });
});

describe('PuffsBackground', () => {
  let ctx: Record<string, ReturnType<typeof vi.fn>>;
  let rafCallbacks: Map<number, FrameRequestCallback>;
  let nextRaf: number;
  let observerCallback: () => void;
  let disconnect: ReturnType<typeof vi.fn>;
  let reduced: boolean;

  beforeEach(() => {
    reduced = false;
    ctx = {
      setTransform: vi.fn(),
      scale: vi.fn(),
      clearRect: vi.fn(),
      beginPath: vi.fn(),
      arc: vi.fn(),
      fill: vi.fn(),
      closePath: vi.fn(),
      createRadialGradient: vi.fn(() => ({ addColorStop: vi.fn() })),
    };
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(
      ctx as unknown as CanvasRenderingContext2D,
    );
    vi.spyOn(HTMLCanvasElement.prototype, 'getBoundingClientRect').mockReturnValue({
      width: 200,
      height: 100,
    } as DOMRect);

    rafCallbacks = new Map();
    nextRaf = 1;
    vi.stubGlobal('requestAnimationFrame', (cb: FrameRequestCallback) => {
      rafCallbacks.set(nextRaf, cb);
      return nextRaf++;
    });
    vi.stubGlobal('cancelAnimationFrame', (id: number) => rafCallbacks.delete(id));

    disconnect = vi.fn();
    vi.stubGlobal(
      'ResizeObserver',
      class {
        constructor(cb: () => void) {
          observerCallback = cb;
        }
        observe = vi.fn();
        disconnect = disconnect;
      },
    );
    vi.stubGlobal('matchMedia', () => ({
      matches: reduced,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }));
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  /** Runs every queued frame callback once, at `timestamp`. */
  function runFrames(timestamp: number) {
    const pending = Array.from(rafCallbacks.entries());
    rafCallbacks.clear();
    act(() => pending.forEach(([, cb]) => cb(timestamp)));
  }

  it('renders a decorative canvas that fades in', () => {
    const { container } = render(<PuffsBackground className="bg" fadeInSeconds={3} />);
    const canvas = container.querySelector('canvas')!;
    expect(canvas).toHaveAttribute('aria-hidden', 'true');
    expect(canvas).toHaveClass('bg');
    expect(canvas.style.getPropertyValue('--puffs-fade-duration')).toBe('3s');
    const before = canvas.className;
    runFrames(0);
    expect(canvas.className).not.toBe(before);
  });

  it('sizes the canvas for the device pixel ratio', () => {
    vi.stubGlobal('devicePixelRatio', 2);
    const { container } = render(<PuffsBackground />);
    const canvas = container.querySelector('canvas')!;
    expect(canvas.width).toBe(400);
    expect(canvas.height).toBe(200);
    expect(ctx.scale).toHaveBeenCalledWith(2, 2);
  });

  it('animates by drawing a frame per animation tick', () => {
    render(<PuffsBackground quantity={10} sets={2} />);
    ctx.arc!.mockClear();
    runFrames(1000);
    expect(ctx.clearRect).toHaveBeenCalled();
    expect(ctx.arc).toHaveBeenCalledTimes(10);
    expect(rafCallbacks.size).toBeGreaterThan(0);
  });

  it('draws one static frame and never loops under reduced motion', () => {
    reduced = true;
    const { container } = render(<PuffsBackground quantity={10} sets={2} />);
    expect(ctx.arc).toHaveBeenCalledTimes(10);
    expect(rafCallbacks.size).toBe(0);
    expect(container.querySelector('canvas')!.className).toMatch(/\S+\s+\S+/);

    ctx.arc!.mockClear();
    act(() => observerCallback());
    expect(ctx.arc).toHaveBeenCalledTimes(10);
  });

  it('re-seeds without redrawing on resize when animating', () => {
    render(<PuffsBackground />);
    ctx.clearRect!.mockClear();
    act(() => observerCallback());
    expect(ctx.clearRect).not.toHaveBeenCalled();
  });

  it('pauses while the tab is hidden and resumes when visible', () => {
    render(<PuffsBackground />);
    runFrames(0);
    expect(rafCallbacks.size).toBe(1);

    Object.defineProperty(document, 'hidden', { value: true, configurable: true });
    act(() => void document.dispatchEvent(new Event('visibilitychange')));
    expect(rafCallbacks.size).toBe(0);

    Object.defineProperty(document, 'hidden', { value: false, configurable: true });
    act(() => void document.dispatchEvent(new Event('visibilitychange')));
    expect(rafCallbacks.size).toBe(1);
  });

  it('does not restart the loop on visibility when reduced motion is on', () => {
    reduced = true;
    render(<PuffsBackground />);
    Object.defineProperty(document, 'hidden', { value: false, configurable: true });
    act(() => void document.dispatchEvent(new Event('visibilitychange')));
    expect(rafCallbacks.size).toBe(0);
  });

  it('stops drawing and disconnects on unmount', () => {
    const { unmount } = render(<PuffsBackground />);
    unmount();
    expect(disconnect).toHaveBeenCalled();
    expect(rafCallbacks.size).toBe(0);
  });

  it('renders nothing to draw when the canvas has no 2d context', () => {
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null);
    render(<PuffsBackground />);
    expect(ctx.clearRect).not.toHaveBeenCalled();
  });
});
