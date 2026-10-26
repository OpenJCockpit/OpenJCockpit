import { act, render } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { FrameOctagon, buildFrameOctagonPaths } from './FrameOctagon';

function renderInHost(width: number, height: number, ui = <FrameOctagon />) {
  const host = document.createElement('div');
  Object.defineProperty(host, 'offsetWidth', { value: width, configurable: true });
  Object.defineProperty(host, 'offsetHeight', { value: height, configurable: true });
  document.body.appendChild(host);
  const result = render(ui, { container: host });
  return { host, ...result };
}

afterEach(() => {
  vi.unstubAllGlobals();
  document.body.innerHTML = '';
});

describe('FrameOctagon', () => {
  it('renders a decorative svg with the measured paths', () => {
    vi.stubGlobal('ResizeObserver', undefined);
    const { host } = renderInHost(200, 100);
    const svg = host.querySelector('svg')!;
    expect(svg).toHaveAttribute('aria-hidden', 'true');
    expect(svg).toHaveAttribute('focusable', 'false');
    const paths = Array.from(svg.querySelectorAll('path')).map((p) => p.getAttribute('d'));
    const { bg, lineA, lineB } = buildFrameOctagonPaths(200, 100);
    expect(paths).toEqual([bg, lineA, lineB]);
  });

  it('measures once even when ResizeObserver is unavailable', () => {
    vi.stubGlobal('ResizeObserver', undefined);
    const { host } = renderInHost(120, 60);
    expect(host.querySelector('path')?.getAttribute('d')).toBe(buildFrameOctagonPaths(120, 60).bg);
  });

  it('applies className to the svg and sets the draw duration', () => {
    vi.stubGlobal('ResizeObserver', undefined);
    const { host } = renderInHost(10, 10, <FrameOctagon className="intro" duration={2.5} />);
    const svg = host.querySelector('svg')!;
    expect(svg).toHaveClass('intro');
    expect(svg.style.getPropertyValue('--frame-duration')).toBe('2.5s');
  });

  it('re-measures when the host resizes and disconnects on unmount', () => {
    let callback: () => void = () => {};
    const observe = vi.fn();
    const disconnect = vi.fn();
    vi.stubGlobal(
      'ResizeObserver',
      class {
        constructor(cb: () => void) {
          callback = cb;
        }
        observe = observe;
        disconnect = disconnect;
      },
    );
    const { host, unmount } = renderInHost(100, 100);
    expect(observe).toHaveBeenCalledWith(host);

    Object.defineProperty(host, 'offsetWidth', { value: 300, configurable: true });
    act(() => callback());
    expect(host.querySelector('path')?.getAttribute('d')).toBe(buildFrameOctagonPaths(300, 100).bg);
    unmount();
    expect(disconnect).toHaveBeenCalled();
  });
});
