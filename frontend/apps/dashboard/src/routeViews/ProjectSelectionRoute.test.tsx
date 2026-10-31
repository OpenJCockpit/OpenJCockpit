import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import type { Project } from '@openjcockpit/api';
import { ROUTES } from '../routes';
import { ProjectSelectionRoute } from './ProjectSelectionRoute';

vi.mock('../lazyViews', () => ({
  ProjectSelection: ({
    onSelect,
    onBack,
  }: {
    onSelect: (p: Project) => void;
    onBack: () => void;
  }) => (
    <>
      <button type="button" onClick={() => onSelect({ id: 'p1' } as Project)}>
        pick
      </button>
      <button type="button" onClick={onBack}>
        back to home
      </button>
    </>
  ),
}));

function pickFrom(state: unknown) {
  render(
    <MemoryRouter initialEntries={[{ pathname: '/select', state }]}>
      <Routes>
        <Route
          path="/select"
          element={<ProjectSelectionRoute projects={[]} onSelect={vi.fn()} />}
        />
        <Route path={ROUTES.dashboard} element={<p>dashboard</p>} />
        <Route path="/back" element={<p>back</p>} />
        <Route path={ROUTES.home} element={<p>home</p>} />
      </Routes>
    </MemoryRouter>,
  );
  fireEvent.click(screen.getByText('pick'));
}

function goBack() {
  render(
    <MemoryRouter initialEntries={['/select']}>
      <Routes>
        <Route
          path="/select"
          element={<ProjectSelectionRoute projects={[]} onSelect={vi.fn()} />}
        />
        <Route path={ROUTES.home} element={<p>home</p>} />
      </Routes>
    </MemoryRouter>,
  );
  fireEvent.click(screen.getByText('back to home'));
}

describe('ProjectSelectionRoute', () => {
  it('returns to the page the user came from', () => {
    pickFrom({ from: '/back' });
    expect(screen.getByText('back')).toBeInTheDocument();
  });

  it.each([[undefined], [null], [{}], [{ from: 5 }]])(
    'falls back to the dashboard for state %j',
    (state) => {
      pickFrom(state);
      expect(screen.getByText('dashboard')).toBeInTheDocument();
    },
  );

  it('goes back to the home page', () => {
    goBack();
    expect(screen.getByText('home')).toBeInTheDocument();
  });
});
