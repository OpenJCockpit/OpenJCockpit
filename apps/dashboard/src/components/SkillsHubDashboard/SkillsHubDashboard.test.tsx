import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { SkillsHubDashboard } from './SkillsHubDashboard';

describe('SkillsHubDashboard — Settings entry point', () => {
  it('renders the Settings control as a real button, not a dead anchor', () => {
    render(<SkillsHubDashboard onBack={vi.fn()} onSettings={vi.fn()} />);

    const settingsControl = screen.getByRole('button', { name: '⚙ Settings' });
    expect(settingsControl.tagName).toBe('BUTTON');
    expect(settingsControl).not.toHaveAttribute('href');
  });

  it('invokes onSettings when the Settings control is activated', () => {
    const onSettings = vi.fn();
    render(<SkillsHubDashboard onBack={vi.fn()} onSettings={onSettings} />);

    screen.getByRole('button', { name: '⚙ Settings' }).click();

    expect(onSettings).toHaveBeenCalledTimes(1);
  });

  it('invokes onBack when the back control is activated', () => {
    const onBack = vi.fn();
    render(<SkillsHubDashboard onBack={onBack} onSettings={vi.fn()} />);

    screen.getByRole('button', { name: '← Back' }).click();

    expect(onBack).toHaveBeenCalledTimes(1);
  });
});
