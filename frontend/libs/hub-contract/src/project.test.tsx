import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { PROJECTS_PATH, RequireProject, useHubProject } from './project';
import { FakeHubProjectProvider } from './testing';

const alpha = { id: 'a', name: 'Alpha' };

function Selection() {
  const state = useLocation().state as { from: string };
  return <p>pick (from {state.from})</p>;
}

function renderGuarded(selected: typeof alpha | null) {
  return render(
    <FakeHubProjectProvider projects={[alpha]} selected={selected}>
      <MemoryRouter initialEntries={['/work?x=1']}>
        <Routes>
          <Route
            path="/work"
            element={
              <RequireProject>
                <p>work</p>
              </RequireProject>
            }
          />
          <Route path={PROJECTS_PATH} element={<Selection />} />
        </Routes>
      </MemoryRouter>
    </FakeHubProjectProvider>,
  );
}

describe('RequireProject', () => {
  it('renders children when a project is selected', () => {
    renderGuarded(alpha);
    expect(screen.getByText('work')).toBeInTheDocument();
  });

  it('sends the user to project selection, remembering where they came from', () => {
    renderGuarded(null);
    expect(screen.getByText('pick (from /work?x=1)')).toBeInTheDocument();
  });
});

describe('useHubProject', () => {
  function Probe() {
    const { projects, selected, select } = useHubProject();
    select('a');
    return (
      <p>
        {projects.length} {selected?.name}
      </p>
    );
  }

  it('returns what the host provides', () => {
    const select = vi.fn();
    render(
      <FakeHubProjectProvider projects={[alpha]} selected={alpha} onSelect={select}>
        <Probe />
      </FakeHubProjectProvider>,
    );
    expect(screen.getByText('1 Alpha')).toBeInTheDocument();
    expect(select).toHaveBeenCalledWith('a');
  });

  it('defaults to no projects and ignores selections', () => {
    render(
      <FakeHubProjectProvider>
        <Probe />
      </FakeHubProjectProvider>,
    );
    expect(screen.getByText('0')).toBeInTheDocument();
  });

  it('throws without a provider', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    expect(() => render(<Probe />)).toThrow('HubProjectContext');
  });
});
