import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { StatusKind } from './StatusChip';
import { StatusChip } from './StatusChip';
import styles from './StatusChip.module.scss';

const KINDS: StatusKind[] = [
  'active',
  'done',
  'waiting',
  'open',
  'review',
  'changes',
  'ok',
  'neutral',
  'error',
];

describe('StatusChip', () => {
  it.each(KINDS)('styles the %s kind with the base chip plus a kind class', (kind) => {
    render(<StatusChip label={kind} kind={kind} />);
    const chip = screen.getByText(kind);
    expect(chip).toHaveClass(styles.statusChip);
    expect(chip.classList).toHaveLength(2);
    expect(chip.className).not.toContain('undefined');
  });

  it('draws the icon before the label, separated by a space', () => {
    render(<StatusChip label="Done" kind="done" icon={<i data-testid="glyph" />} />);
    const chip = screen.getByText(/Done/);
    expect(chip.firstChild).toBe(screen.getByTestId('glyph'));
    expect(chip.textContent).toBe(' Done');
  });
});
