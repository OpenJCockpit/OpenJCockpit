import { render } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { LandingHubRoute } from './LandingHubRoute';

vi.mock('@openjcockpit/landing-hub', () => ({ LandingHub: () => null }));

describe('LandingHubRoute', () => {
  it('throws when the session has no user', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);

    expect(() =>
      render(<LandingHubRoute user={null} onSelectPortal={vi.fn()} onLogout={vi.fn()} />),
    ).toThrow('Authenticated session has no user');
  });
});
