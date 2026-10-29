import { fireEvent, screen } from '@testing-library/react';
import { render } from '../testRender';
import { describe, expect, it, vi } from 'vitest';
import type { Project } from '@openjcockpit/api';
import { ProjectSelection } from './ProjectSelection';

function project(overrides: Partial<Project> = {}): Project {
  return {
    id: 'p1',
    name: 'Alpha',
    active: 1,
    newProject: 0,
    gitStatus: 'ACCESSIBLE',
    hasCredentials: false,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

function setup(projects: Project[]) {
  const onSelect = vi.fn();
  const onSettings = vi.fn();
  render(<ProjectSelection projects={projects} onSelect={onSelect} onSettings={onSettings} />);
  return { onSelect, onSettings };
}

describe('ProjectSelection', () => {
  it('shows the empty state and opens settings from it', () => {
    const { onSettings } = setup([]);
    expect(screen.getByTestId('project-selection-empty')).toBeInTheDocument();
    fireEvent.click(screen.getByText('Manage projects →'));
    expect(onSettings).toHaveBeenCalledTimes(1);
  });

  it('opens settings from the footer', () => {
    const { onSettings } = setup([project()]);
    fireEvent.click(screen.getByText('⚙ Manage projects'));
    expect(onSettings).toHaveBeenCalledTimes(1);
  });

  it('selects an accessible project on click', () => {
    const p = project();
    const { onSelect } = setup([p]);
    fireEvent.click(screen.getByTestId('project-card'));
    expect(onSelect).toHaveBeenCalledWith(p);
  });

  it('renders the optional metadata', () => {
    setup([
      project({
        description: 'Does things',
        environment: 'prod',
        owner: 'team-a',
        defaultBranch: 'main',
      }),
    ]);
    expect(screen.getByText('Does things')).toBeInTheDocument();
    expect(screen.getByText('prod')).toBeInTheDocument();
    expect(screen.getByText('Owner: team-a')).toBeInTheDocument();
    expect(screen.getByText('Branch: main')).toBeInTheDocument();
  });

  it.each([
    ['ACCESSIBLE', 0, true],
    ['UNKNOWN', 0, true],
    ['NOT_ACCESSIBLE', 1, true],
    ['NOT_ACCESSIBLE', 0, false],
    ['CHECK_FAILED', 0, false],
  ] as const)('gitStatus %s newProject %i selectable=%s', (gitStatus, newProject, selectable) => {
    setup([project({ gitStatus, newProject })]);
    const card = screen.getByTestId('project-card');
    if (selectable) expect(card).toBeEnabled();
    else expect(card).toBeDisabled();
  });

  it('never selects an inactive project', () => {
    const { onSelect } = setup([project({ active: 0 })]);
    const card = screen.getByTestId('project-card');
    expect(card).toBeDisabled();
    fireEvent.click(card);
    fireEvent.keyDown(card, { key: 'Enter' });
    expect(onSelect).not.toHaveBeenCalled();
  });

  it('selects on Enter', () => {
    const p = project();
    const { onSelect } = setup([p]);
    fireEvent.keyDown(screen.getByTestId('project-card'), { key: 'Enter' });
    expect(onSelect).toHaveBeenCalledWith(p);
  });

  it.each([
    ['ACCESSIBLE', 'Git repository reachable'],
    ['NOT_ACCESSIBLE', 'Git repository not reachable'],
    ['CHECK_FAILED', 'Git repository not reachable'],
    ['UNKNOWN', 'Git status unknown'],
  ] as const)('labels %s as "%s"', (gitStatus, label) => {
    setup([project({ gitStatus })]);
    expect(screen.getByLabelText(label)).toBeInTheDocument();
  });

  it('warns that an unreachable existing repository must be fixed in settings', () => {
    setup([project({ gitStatus: 'NOT_ACCESSIBLE', newProject: 0 })]);
    expect(screen.getByText(/Repository not reachable/)).toBeInTheDocument();
  });

  it('does not warn for a new project whose repository does not exist yet', () => {
    setup([project({ gitStatus: 'NOT_ACCESSIBLE', newProject: 1 })]);
    expect(screen.queryByText(/Repository not reachable/)).not.toBeInTheDocument();
  });
});
