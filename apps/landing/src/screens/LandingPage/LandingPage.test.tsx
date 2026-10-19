import { act, render, screen } from '@testing-library/react';
import type Keycloak from 'keycloak-js';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import LandingPage from './LandingPage';

// jsdom has no canvas; the particle background is not under test here.
vi.mock('../../ui/backgrounds/PuffsBackground', () => ({ default: () => null }));
vi.mock('../../ui/vendor/react-bits/DecipherText', () => ({
  default: ({ text }: { text: string }) => <h1>{text}</h1>,
}));

const keycloak = { logout: vi.fn() } as unknown as Keycloak;
const user = { id: '1', username: 'tstark', firstName: 'Tony', email: undefined };

beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());

describe('LandingPage', () => {
  it('renders the hero with both portals', () => {
    render(<LandingPage user={user} keycloak={keycloak} />);

    expect(screen.getByTestId('landing-hero')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Choose your workspace' })).toBeInTheDocument();
    expect(screen.getByTestId('portal-orb-business')).toBeInTheDocument();
    expect(screen.getByTestId('portal-orb-implementation')).toBeInTheDocument();
    expect(screen.getByText(/Welcome back, Tony\./)).toBeInTheDocument();
  });

  it('shows the intro greeting, then removes it', () => {
    render(<LandingPage user={user} keycloak={keycloak} />);
    expect(screen.getByTestId('intro-greeting')).toBeInTheDocument();

    act(() => void vi.advanceTimersByTime(2850));

    expect(screen.queryByTestId('intro-greeting')).not.toBeInTheDocument();
    expect(screen.getByTestId('landing-hero')).toBeInTheDocument();
  });

  it('falls back to a default name when the user has none', () => {
    render(<LandingPage user={{ ...user, firstName: '' }} keycloak={keycloak} />);
    expect(screen.getByText(/Welcome back, Tony\./)).toBeInTheDocument();
  });
});
