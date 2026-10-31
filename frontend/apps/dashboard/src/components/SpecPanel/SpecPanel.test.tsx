import { fireEvent, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { render } from '../../testUtils/render';
import { SpecPanel } from './SpecPanel';

function setup(props: { onRetry?: () => void } = {}) {
  render(
    <SpecPanel
      specs={[]}
      loading={false}
      error
      onRetry={props.onRetry}
      selectedSpecId=""
      selected={undefined}
      onSelect={vi.fn()}
      onGoToWorkflow={vi.fn()}
      onViewAll={vi.fn()}
    />,
  );
}

describe('SpecPanel', () => {
  it('offers a retry when the specs failed to load', () => {
    const onRetry = vi.fn();
    setup({ onRetry });
    expect(screen.getByTestId('specs-error')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('shows the failure without a retry button when nothing can retry', () => {
    setup();
    expect(screen.getByTestId('specs-error')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument();
  });
});
