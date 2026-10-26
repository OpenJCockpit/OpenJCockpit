import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { usePrefersReducedMotion, getPrefersReducedMotion } from './usePrefersReducedMotion';

type Listener = (event: MediaQueryListEvent) => void;

function stubMatchMedia(matches: boolean) {
  const listeners = new Set<Listener>();
  const mql = {
    matches,
    addEventListener: (_: string, l: Listener) => listeners.add(l),
    removeEventListener: (_: string, l: Listener) => listeners.delete(l),
  };
  vi.stubGlobal('matchMedia', vi.fn().mockReturnValue(mql));
  return {
    listeners,
    emit: (next: boolean) => listeners.forEach((l) => l({ matches: next } as MediaQueryListEvent)),
  };
}

afterEach(() => vi.unstubAllGlobals());

describe('usePrefersReducedMotion', () => {
  it('is false when matchMedia is unavailable', () => {
    vi.stubGlobal('matchMedia', undefined);
    expect(getPrefersReducedMotion()).toBe(false);
    expect(renderHook(() => usePrefersReducedMotion()).result.current).toBe(false);
  });

  it('reads the initial preference', () => {
    stubMatchMedia(true);
    expect(renderHook(() => usePrefersReducedMotion()).result.current).toBe(true);
  });

  it('uses the legacy addListener API when addEventListener is missing', () => {
    const listeners = new Set<Listener>();
    const mql = {
      matches: false,
      addListener: (l: Listener) => listeners.add(l),
      removeListener: (l: Listener) => listeners.delete(l),
    };
    vi.stubGlobal('matchMedia', vi.fn().mockReturnValue(mql));
    const { result, unmount } = renderHook(() => usePrefersReducedMotion());

    act(() => listeners.forEach((l) => l({ matches: true } as MediaQueryListEvent)));
    expect(result.current).toBe(true);

    unmount();
    expect(listeners.size).toBe(0);
  });

  it('follows changes and unsubscribes on unmount', () => {
    const media = stubMatchMedia(false);
    const { result, unmount } = renderHook(() => usePrefersReducedMotion());
    expect(result.current).toBe(false);

    act(() => media.emit(true));
    expect(result.current).toBe(true);

    unmount();
    expect(media.listeners.size).toBe(0);
  });
});
