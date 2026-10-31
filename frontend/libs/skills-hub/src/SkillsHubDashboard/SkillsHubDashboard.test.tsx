import { screen } from '@testing-library/react';
import { renderWithI18n } from '@openjcockpit/i18n/testing';
import { skillsHubResources } from '../i18n';
import { describe, expect, it, vi } from 'vitest';
import { SkillsHubDashboard } from './SkillsHubDashboard';

describe('SkillsHubDashboard — Settings entry point', () => {
  it('renders the Settings control as a real button, not a dead anchor', () => {
    renderWithI18n(<SkillsHubDashboard onBack={vi.fn()} onSettings={vi.fn()} />, {
      resources: skillsHubResources,
    });

    const settingsControl = screen.getByRole('button', { name: '⚙ Settings' });
    expect(settingsControl.tagName).toBe('BUTTON');
    expect(settingsControl).not.toHaveAttribute('href');
  });

  it('invokes onSettings when the Settings control is activated', () => {
    const onSettings = vi.fn();
    renderWithI18n(<SkillsHubDashboard onBack={vi.fn()} onSettings={onSettings} />, {
      resources: skillsHubResources,
    });

    screen.getByRole('button', { name: '⚙ Settings' }).click();

    expect(onSettings).toHaveBeenCalledTimes(1);
  });

  it('invokes onBack when the back control is activated', () => {
    const onBack = vi.fn();
    renderWithI18n(<SkillsHubDashboard onBack={onBack} onSettings={vi.fn()} />, {
      resources: skillsHubResources,
    });

    screen.getByRole('button', { name: '← Back to dashboard' }).click();

    expect(onBack).toHaveBeenCalledTimes(1);
  });
});
