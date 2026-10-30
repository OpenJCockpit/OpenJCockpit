import { beforeEach, describe, expect, it } from 'vitest';
import { act, render, screen, within } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router';
import { withI18n } from '@openjcockpit/i18n/testing';
import { AppRegistryProvider, type AppDefinition } from '@openjcockpit/hub-contract';
import { FakeHubSession, FakeHubSessionProvider } from '@openjcockpit/hub-contract/testing';
import { appResources } from '../i18nResources';
import { clickAsync } from '../testUtils/dom';
import { Home } from './Home';

const widget = (id: string, over = {}) => ({
  id,
  titleKey: `title-${id}`,
  sourceAppId: 'demo',
  requiredPermissions: [],
  component: () => Promise.resolve({ default: () => <p>body {id}</p> }),
  defaultFor: [],
  link: `/${id}` as const,
  ...over,
});

const demo: AppDefinition = {
  id: 'demo',
  labelKey: 'demo',
  icon: 'x',
  route: '/demo',
  order: 1,
  requiredPermissions: [],
  loadRoutes: () => Promise.resolve([]),
  widgets: [
    widget('one'),
    widget('two'),
    widget('admin-only', { requiredPermissions: ['admin'] }),
    widget('opt-in', { defaultFor: ['nobody'] }),
  ],
  quickActions: [
    { id: 'go', labelKey: 'Go somewhere', requiredPermissions: [], link: '/somewhere' },
  ],
};

function Where() {
  return <p data-testid="where">{useLocation().pathname}</p>;
}

async function renderHome(permissions: string[] = []) {
  await act(async () => {
    render(
      withI18n(
        <FakeHubSessionProvider session={new FakeHubSession(permissions)}>
          <AppRegistryProvider apps={[demo]}>
            <MemoryRouter>
              <Home />
              <Where />
            </MemoryRouter>
          </AppRegistryProvider>
        </FakeHubSessionProvider>,
        appResources,
      ),
    );
  });
}

const order = () =>
  within(screen.getByTestId('home-grid'))
    .getAllByRole('article')
    .map((el) => el.dataset.testid);

describe('Home', () => {
  beforeEach(() => localStorage.clear());

  it('shows the default widgets the user may see, with their bodies', async () => {
    await renderHome();
    expect(order()).toEqual(['widget-one', 'widget-two']);
    expect(await screen.findByText('body one')).toBeInTheDocument();
  });

  it('shows permission-gated widgets only to those who hold the permission', async () => {
    await renderHome(['admin']);
    expect(order()).toContain('widget-admin-only');
  });

  it('runs quick actions and opens a widget page', async () => {
    await renderHome();
    await clickAsync(screen.getByTestId('quick-action-go'));
    expect(screen.getByTestId('where')).toHaveTextContent('/somewhere');
  });

  it('opens the widget page from the card', async () => {
    await renderHome();
    await clickAsync(screen.getByTestId('widget-open-one'));
    expect(screen.getByTestId('where')).toHaveTextContent('/one');
  });

  it('customises: reorders, resizes, removes, adds, and persists per user', async () => {
    await renderHome();
    await clickAsync(screen.getByTestId('home-customise'));

    await clickAsync(screen.getByTestId('widget-later-one'));
    expect(order().slice(0, 2)).toEqual(['widget-two', 'widget-one']);

    await clickAsync(screen.getByTestId('widget-wider-two'));
    expect(screen.getByTestId('widget-two')).toHaveAttribute('data-cols', '9');
    await clickAsync(screen.getByTestId('widget-taller-two'));
    expect(screen.getByTestId('widget-two')).toHaveAttribute('data-rows', '2');

    await clickAsync(screen.getByTestId('widget-remove-one'));
    expect(order()).toEqual(['widget-two']);
    await clickAsync(screen.getByTestId('widget-add-opt-in'));
    await clickAsync(screen.getByTestId('widget-add-one'));
    expect(order()).toEqual(['widget-two', 'widget-opt-in', 'widget-one']);

    const stored = localStorage.getItem('openjcockpit.homeLayout.fake-user');
    expect(JSON.parse(stored ?? 'null')[0]).toEqual({ id: 'two', cols: 9, rows: 2 });
  });

  it('restores a saved layout on the next visit and resets to the default', async () => {
    localStorage.setItem(
      'openjcockpit.homeLayout.fake-user',
      JSON.stringify([{ id: 'two', cols: 12, rows: 1 }]),
    );
    await renderHome();
    expect(order()).toEqual(['widget-two']);

    await clickAsync(screen.getByTestId('home-customise'));
    await clickAsync(screen.getByTestId('home-reset'));
    expect(order()).toEqual(['widget-one', 'widget-two']);
    expect(localStorage.getItem('openjcockpit.homeLayout.fake-user')).toBeNull();
  });

  it('disables moving at the ends and hides the controls outside customise mode', async () => {
    await renderHome();
    expect(screen.queryByTestId('widget-later-one')).not.toBeInTheDocument();
    await clickAsync(screen.getByTestId('home-customise'));
    expect(screen.getByTestId('widget-earlier-one')).toBeDisabled();
    expect(screen.getByTestId('widget-later-two')).toBeDisabled();
  });

  it('explains an empty Home', async () => {
    localStorage.setItem('openjcockpit.homeLayout.fake-user', '[]');
    await renderHome();
    expect(screen.getByTestId('home-empty')).toBeInTheDocument();
  });
});
