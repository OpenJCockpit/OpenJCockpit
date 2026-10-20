import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { StatusKind } from '@openjcockpit/api';
import { StatusChip } from './StatusChip';
import styles from './StatusChip.module.scss';

const KINDS: StatusKind[] = ['active', 'done', 'waiting', 'open', 'review', 'changes', 'ok'];

describe('StatusChip', () => {
  it.each(KINDS)('styles the %s kind with the base chip plus a kind class', (kind) => {
    render(<StatusChip label={kind} kind={kind} />);
    const chip = screen.getByText(kind);
    expect(chip).toHaveClass(styles.statusChip);
    expect(chip.classList).toHaveLength(2);
    expect(chip.className).not.toContain('undefined');
  });
});
