import { describe, expect, it, vi } from 'vitest';
import { renderHook } from '@testing-library/react';
import { isAllowed, useHubSession } from './session';
import { FakeHubSession, FakeHubSessionProvider } from './testing';

describe('useHubSession', () => {
  it('returns the provided session', () => {
    const session = new FakeHubSession();
    const { result } = renderHook(() => useHubSession(), {
      wrapper: ({ children }) => (
        <FakeHubSessionProvider session={session}>{children}</FakeHubSessionProvider>
      ),
    });
    expect(result.current).toBe(session);
  });

  it('throws a clear error without a provider', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    expect(() => renderHook(() => useHubSession())).toThrow(/HubSessionContext provider/);
  });
});

describe('isAllowed', () => {
  const session = new FakeHubSession(['a.read']);

  it('lets everyone through an empty requirement', () => {
    expect(isAllowed(session, [])).toBe(true);
  });

  it('needs any one of the permissions', () => {
    expect(isAllowed(session, ['x', 'a.read'])).toBe(true);
    expect(isAllowed(session, ['x'])).toBe(false);
  });
});
