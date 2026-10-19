import { fireEvent, render, screen } from '@testing-library/react';
import type Keycloak from 'keycloak-js';
import { describe, expect, it, vi } from 'vitest';
import TopBar from './TopBar';

const user = { id: '1', username: 'tstark', firstName: 'Tony', email: 'tony@example.com' };

function setup(overrides: Partial<typeof user> = {}) {
  const logout = vi.fn();
  render(<TopBar user={{ ...user, ...overrides }} keycloak={{ logout } as unknown as Keycloak} />);
  return { logout };
}

describe('TopBar', () => {
  it('shows the email, falling back to the username', () => {
    setup();
    expect(screen.getByText('tony@example.com')).toBeInTheDocument();
  });

  it('falls back to the username without an email', () => {
    setup({ email: undefined });
    expect(screen.getByText('tstark')).toBeInTheDocument();
  });

  it('opens a popover per action and toggles it closed', () => {
    setup();
    expect(screen.queryByText('No new notifications.')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /Notifications/ }));
    expect(screen.getByText('No new notifications.')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /Messages/ }));
    expect(screen.queryByText('No new notifications.')).not.toBeInTheDocument();
    expect(screen.getByText('No new messages.')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /Messages/ }));
    expect(screen.queryByText('No new messages.')).not.toBeInTheDocument();
  });

  it('describes the logged-in user in the profile popover', () => {
    setup();
    fireEvent.click(screen.getByRole('button', { name: /Profile/ }));
    expect(screen.getByText(/Logged in as Tony \(tstark\)/)).toBeInTheDocument();
  });

  it('logs out back to the current origin', () => {
    const { logout } = setup();
    fireEvent.click(screen.getByRole('button', { name: 'Logout' }));
    expect(logout).toHaveBeenCalledWith({ redirectUri: window.location.origin });
  });
});
