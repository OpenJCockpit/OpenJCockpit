import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LandingPage } from './LandingPage';

// jsdom has no canvas; the particle background is not under test here.
vi.mock('@openjcockpit/ui', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@openjcockpit/ui')>()),
  PuffsBackground: () => null,
  DecipherText: ({ text }: { text: string }) => <h1>{text}</h1>,
}));

const handlers = { onSelectPortal: vi.fn(), onLogout: vi.fn() };
const user = { username: 'tstark', firstName: 'Tony', email: undefined };

beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());

describe('LandingPage', () => {
  it('renders the hero with both portals', () => {
    render(<LandingPage user={user} {...handlers} />);

    expect(screen.getByTestId('landing-hero')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Choose your workspace' })).toBeInTheDocument();
    expect(screen.getByTestId('portal-orb-business')).toBeInTheDocument();
    expect(screen.getByTestId('portal-orb-implementation')).toBeInTheDocument();
    expect(screen.getByText(/Welcome back, Tony\./)).toBeInTheDocument();
  });

  it('shows the intro greeting, then removes it', () => {
    render(<LandingPage user={user} {...handlers} />);
    expect(screen.getByTestId('intro-greeting')).toBeInTheDocument();

    act(() => void vi.advanceTimersByTime(2850));

    expect(screen.queryByTestId('intro-greeting')).not.toBeInTheDocument();
    expect(screen.getByTestId('landing-hero')).toBeInTheDocument();
  });

  it('reports the selected portal', () => {
    render(<LandingPage user={user} {...handlers} />);

    fireEvent.click(screen.getByTestId('portal-orb-business'));
    expect(handlers.onSelectPortal).toHaveBeenCalledWith('business');

    fireEvent.click(screen.getByTestId('portal-orb-implementation'));
    expect(handlers.onSelectPortal).toHaveBeenCalledWith('implementation');
  });

  it('reports logout', () => {
    render(<LandingPage user={user} {...handlers} />);
    fireEvent.click(screen.getByRole('button', { name: 'Logout' }));
    expect(handlers.onLogout).toHaveBeenCalledTimes(1);
  });

  it('falls back to a default name when the user has none', () => {
    render(<LandingPage user={{ ...user, firstName: '' }} {...handlers} />);
    expect(screen.getByText(/Welcome back, Tony\./)).toBeInTheDocument();
  });
});
