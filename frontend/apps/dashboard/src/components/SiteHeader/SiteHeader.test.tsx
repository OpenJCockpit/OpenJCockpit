import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import type { Project } from '@openjcockpit/api';
import { ROUTES } from '../../routes';
import { SiteHeader } from './SiteHeader';
import type { SiteSection } from './SiteHeader';

function project(id: string, name: string): Project {
  return { id, name } as Project;
}

function Location() {
  return <p data-testid="location">{useLocation().pathname}</p>;
}

function setup(
  props: Partial<{
    active: SiteSection;
    projects: Project[];
    selectedProject: Project | null;
  }> = {},
) {
  const onProjectChange = vi.fn();
  const onLogout = vi.fn();
  render(
    <MemoryRouter initialEntries={['/start']}>
      <SiteHeader
        active={props.active ?? 'platform'}
        projects={props.projects ?? []}
        selectedProject={props.selectedProject ?? null}
        onProjectChange={onProjectChange}
        onLogout={onLogout}
      />
      <Location />
    </MemoryRouter>,
  );
  return { onProjectChange, onLogout };
}

const path = () => screen.getByTestId('location').textContent;

describe('SiteHeader', () => {
  it.each([
    ['nav-platform', ROUTES.dashboard],
    ['nav-workflow', ROUTES.workflow],
    ['nav-spec-files', ROUTES.specFiles],
    ['nav-spec-queue', ROUTES.specQueue],
    ['nav-skills-hub', ROUTES.skillsHub],
    ['nav-hub', ROUTES.landing],
  ])('%s navigates to %s', (testId, route) => {
    setup();
    fireEvent.click(screen.getByTestId(testId));
    expect(path()).toBe(route);
  });

  it('opens project management from the gear', () => {
    setup();
    fireEvent.click(screen.getByTitle('Project management'));
    expect(path()).toBe(ROUTES.settings);
  });

  it('returns to the hub from the logo', () => {
    setup();
    fireEvent.click(screen.getAllByTitle('Back to hub')[0]!);
    expect(path()).toBe(ROUTES.landing);
  });

  it('marks only the active section as the current page', () => {
    setup({ active: 'specQueue' });
    expect(screen.getByTestId('nav-spec-queue')).toHaveAttribute('aria-current', 'page');
    expect(screen.getByTestId('nav-platform')).not.toHaveAttribute('aria-current');
  });

  it('logs out', () => {
    const { onLogout } = setup();
    fireEvent.click(screen.getByTestId('nav-logout'));
    expect(onLogout).toHaveBeenCalledTimes(1);
  });

  it('hides the project picker when there are no projects', () => {
    setup();
    expect(screen.queryByTitle('Active project')).not.toBeInTheDocument();
  });

  it('shows the selected project and reports changes', () => {
    const a = project('a', 'Alpha');
    const b = project('b', 'Beta');
    const { onProjectChange } = setup({ projects: [a, b], selectedProject: b });
    const select = screen.getByTitle('Active project');
    expect(select).toHaveValue('b');
    fireEvent.change(select, { target: { value: 'a' } });
    expect(onProjectChange).toHaveBeenCalledWith('a');
  });
});
