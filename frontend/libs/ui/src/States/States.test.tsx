import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { EmptyState, ErrorState, LoadingState } from './States';

describe('LoadingState', () => {
  it('announces its text politely', () => {
    render(<LoadingState>Loading the queue…</LoadingState>);
    expect(screen.getByRole('status')).toHaveTextContent('Loading the queue…');
  });
});

describe('EmptyState', () => {
  it('renders title, hint and action', () => {
    render(<EmptyState title="No specs" hint="Add one" action={<button>Add</button>} />);
    expect(screen.getByText('No specs')).toBeInTheDocument();
    expect(screen.getByText('Add one')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add' })).toBeInTheDocument();
  });

  it('omits the hint when not given', () => {
    render(<EmptyState title="Nothing" />);
    expect(screen.getAllByText(/./)).toHaveLength(1);
  });
});

describe('ErrorState', () => {
  it('is an alert and offers retry only when it can', () => {
    const onRetry = vi.fn();
    const { rerender } = render(<ErrorState>Boom</ErrorState>);
    expect(screen.getByRole('alert')).toHaveTextContent('Boom');
    expect(screen.queryByRole('button')).not.toBeInTheDocument();

    rerender(
      <ErrorState onRetry={onRetry} retryLabel="Retry">
        Boom
      </ErrorState>,
    );
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(onRetry).toHaveBeenCalledOnce();
  });
});
