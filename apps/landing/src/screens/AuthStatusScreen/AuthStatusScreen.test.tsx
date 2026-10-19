import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import AuthStatusScreen from './AuthStatusScreen';

afterEach(() => vi.unstubAllGlobals());

describe('AuthStatusScreen', () => {
  it('shows the redirect message while initializing', () => {
    render(<AuthStatusScreen status="initializing" />);

    expect(screen.getByTestId('auth-status-screen')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Checking authentication' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument();
  });

  it('shows the failure, the error text and a retry button on error', () => {
    const reload = vi.fn();
    vi.stubGlobal('location', { reload });
    render(<AuthStatusScreen status="error" error={new Error('boom')} />);

    expect(screen.getByRole('heading', { name: 'Authentication failed' })).toBeInTheDocument();
    expect(screen.getByText('boom')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it('stringifies non-Error values', () => {
    render(<AuthStatusScreen status="error" error="plain failure" />);
    expect(screen.getByText('plain failure')).toBeInTheDocument();
  });
});
