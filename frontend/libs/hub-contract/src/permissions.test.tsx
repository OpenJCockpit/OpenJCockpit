import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { Can, RequirePermission } from './permissions';
import { FakeHubSession, FakeHubSessionProvider } from './testing';

function renderWith(permissions: string[], ui: React.ReactNode) {
  return render(
    <FakeHubSessionProvider session={new FakeHubSession(permissions)}>
      <MemoryRouter initialEntries={['/app']}>
        <Routes>
          <Route path="/app" element={ui} />
          <Route path="/403" element={<p>forbidden</p>} />
        </Routes>
      </MemoryRouter>
    </FakeHubSessionProvider>,
  );
}

describe('Can', () => {
  it('renders children with the permission', () => {
    renderWith(['x'], <Can permission="x">shown</Can>);
    expect(screen.getByText('shown')).toBeInTheDocument();
  });

  it('renders nothing without it', () => {
    renderWith([], <Can permission="x">shown</Can>);
    expect(screen.queryByText('shown')).not.toBeInTheDocument();
  });
});

describe('RequirePermission', () => {
  it('renders children when any permission matches', () => {
    renderWith(['y'], <RequirePermission any={['x', 'y']}>page</RequirePermission>);
    expect(screen.getByText('page')).toBeInTheDocument();
  });

  it('redirects to /403 otherwise', () => {
    renderWith([], <RequirePermission any={['x']}>page</RequirePermission>);
    expect(screen.getByText('forbidden')).toBeInTheDocument();
  });
});
