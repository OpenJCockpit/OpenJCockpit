import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorBoundary } from './ErrorBoundary';

function Boom({ explode }: { explode: boolean }) {
  if (explode) throw new Error('kaboom');
  return <p>fine</p>;
}

describe('ErrorBoundary', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });
  afterEach(() => vi.restoreAllMocks());

  it('renders children when nothing throws', () => {
    render(
      <ErrorBoundary>
        <Boom explode={false} />
      </ErrorBoundary>,
    );
    expect(screen.getByText('fine')).toBeInTheDocument();
  });

  it('shows the fallback with a reload action on a render error', () => {
    render(
      <ErrorBoundary message="Oops">
        <Boom explode />
      </ErrorBoundary>,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('Oops');
    expect(screen.getByRole('button', { name: 'Reload' })).toBeInTheDocument();
  });

  it('reloads the page when Reload is clicked', () => {
    const reload = vi.fn();
    vi.stubGlobal('location', { ...window.location, reload });
    render(
      <ErrorBoundary>
        <Boom explode />
      </ErrorBoundary>,
    );
    expect(screen.getByText('Something went wrong.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Reload' }));
    expect(reload).toHaveBeenCalledTimes(1);
    vi.unstubAllGlobals();
  });

  it('recovers when a reset key changes', () => {
    const { rerender } = render(
      <ErrorBoundary resetKeys={['a']}>
        <Boom explode />
      </ErrorBoundary>,
    );
    expect(screen.getByRole('alert')).toBeInTheDocument();
    rerender(
      <ErrorBoundary resetKeys={['b']}>
        <Boom explode={false} />
      </ErrorBoundary>,
    );
    expect(screen.getByText('fine')).toBeInTheDocument();
  });
});
