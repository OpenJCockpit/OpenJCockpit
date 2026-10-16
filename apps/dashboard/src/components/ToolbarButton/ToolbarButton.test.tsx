import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { buttonClass } from '../../ui/Button/Button';
import { ToolbarButton } from './ToolbarButton';

describe('ToolbarButton', () => {
  it('defaults to the primary variant and handles clicks', () => {
    const onClick = vi.fn();
    render(<ToolbarButton onClick={onClick}>Go</ToolbarButton>);
    const button = screen.getByRole('button', { name: 'Go' });
    expect(button.className).toBe(buttonClass({ variant: 'primary' }));
    fireEvent.click(button);
    expect(onClick).toHaveBeenCalledOnce();
  });

  it('supports the small variant and disabled', () => {
    render(
      <ToolbarButton variant="small" disabled>
        Small
      </ToolbarButton>,
    );
    const button = screen.getByRole('button', { name: 'Small' });
    expect(button.className).toBe(buttonClass({ size: 'sm' }));
    expect(button).toBeDisabled();
  });
});
