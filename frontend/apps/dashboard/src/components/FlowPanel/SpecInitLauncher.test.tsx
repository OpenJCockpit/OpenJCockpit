import { fireEvent, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { render } from '../../testUtils/render';
import { SpecInitLauncher } from './SpecInitLauncher';

// The real Select never reports a change for its single, disabled option, so the callback the
// launcher hands it is only reachable through a stand-in that fires it.
vi.mock('@openjcockpit/ui', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@openjcockpit/ui')>()),
  Select: ({ onChange }: { onChange: (value: string) => void }) => (
    <button type="button" onClick={() => onChange('other')}>
      change workflow
    </button>
  ),
}));

describe('SpecInitLauncher', () => {
  it('starts the initialization and ignores workflow changes', () => {
    const onStart = vi.fn();
    render(<SpecInitLauncher buttonState="idle" error={null} onStart={onStart} />);
    fireEvent.click(screen.getByRole('button', { name: 'change workflow' }));
    fireEvent.click(screen.getByRole('button', { name: '▶ Start' }));
    expect(onStart).toHaveBeenCalledTimes(1);
  });

  it('shows the start error', () => {
    render(<SpecInitLauncher buttonState="error" error="boom" onStart={vi.fn()} />);
    expect(screen.getByText('boom')).toBeInTheDocument();
  });
});
