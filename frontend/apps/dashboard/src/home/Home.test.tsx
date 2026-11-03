import { beforeEach, describe, expect, it } from 'vitest';
import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router';
import { withI18n } from '@openjcockpit/i18n/testing';
import { AppRegistryProvider, type AppDefinition } from '@openjcockpit/hub-contract';
import type { HubProject, HubUser } from '@openjcockpit/hub-contract';
import {
  FakeHubProjectProvider,
  FakeHubSession,
  FakeHubSessionProvider,
} from '@openjcockpit/hub-contract/testing';
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
    widget('opt-in', { defaultFor: ['nobody'], descriptionKey: 'About opt-in' }),
  ],
};

function Where() {
  return <p data-testid="where">{useLocation().pathname}</p>;
}

async function renderHome(
  permissions: string[] = [],
  user?: HubUser | null,
  project: HubProject | null = null,
) {
  const session = new FakeHubSession(permissions);
  if (user !== undefined) session.user = user ?? undefined;
  await act(async () => {
    render(
      withI18n(
        <FakeHubSessionProvider session={session}>
          <FakeHubProjectProvider selected={project}>
          <AppRegistryProvider apps={[demo]}>
            <MemoryRouter>
              <Home />
              <Where />
            </MemoryRouter>
          </AppRegistryProvider>
          </FakeHubProjectProvider>
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

  it('shows the selected project as the page header instead of the greeting', async () => {
    await renderHome([], { firstName: 'Ada', username: 'ada' } as HubUser, {
      id: 'p1',
      name: 'Noordzee',
      gitStatus: 'ACCESSIBLE',
    } as HubProject);
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Noordzee');
    expect(screen.queryByText(/Ada/)).not.toBeInTheDocument();
  });

  it('shows the default widgets the user may see, with their bodies', async () => {
    await renderHome();
    expect(order()).toEqual(['widget-one', 'widget-two']);
    expect(await screen.findByText('body one')).toBeInTheDocument();
  });

  it('shows permission-gated widgets only to those who hold the permission', async () => {
    await renderHome(['admin']);
    expect(order()).toContain('widget-admin-only');
  });

  const dataTransfer = {
    effectAllowed: '',
    dropEffect: '',
    setData: () => undefined,
    setDragImage: () => undefined,
  };
  const drag = (source: HTMLElement, target: HTMLElement) => {
    fireEvent.dragStart(source, { dataTransfer });
    fireEvent.dragEnter(target, { dataTransfer });
    fireEvent.dragOver(target, { dataTransfer });
    fireEvent.drop(target, { dataTransfer });
    fireEvent.dragEnd(source, { dataTransfer });
  };
  const stored = () =>
    JSON.parse(localStorage.getItem('openjcockpit.homeLayout.v2.fake-user') ?? 'null');

  it('customises on a draft: reorders, resizes, removes, adds, and saves per user on Done', async () => {
    await renderHome();
    await clickAsync(screen.getByTestId('home-customise'));
    expect(screen.getByRole('dialog', { name: 'Customise your Home' })).toBeInTheDocument();

    await clickAsync(screen.getByTestId('widget-later-one'));
    expect(order().slice(0, 2)).toEqual(['widget-two', 'widget-one']);

    const grip = screen.getByTestId('widget-resize-two');
    fireEvent.keyDown(grip, { key: 'ArrowRight' });
    expect(screen.getByTestId('widget-two')).toHaveAttribute('data-cols', '3');
    fireEvent.keyDown(grip, { key: 'ArrowDown' });
    expect(screen.getByTestId('widget-two')).toHaveAttribute('data-rows', '3');

    await clickAsync(screen.getByTestId('widget-remove-one'));
    expect(order()).toEqual(['widget-two']);
    await clickAsync(screen.getByTestId('widget-add-opt-in'));
    await clickAsync(screen.getByTestId('widget-add-one'));
    expect(order()).toEqual(['widget-two', 'widget-opt-in', 'widget-one']);
    expect(stored()).toBeNull();

    await clickAsync(screen.getByTestId('home-done'));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(order()).toEqual(['widget-two', 'widget-opt-in', 'widget-one']);
    expect(stored()[0]).toEqual({ id: 'two', cols: 3, rows: 3 });
  });

  it('cancel drops the draft', async () => {
    await renderHome();
    await clickAsync(screen.getByTestId('home-customise'));
    await clickAsync(screen.getByTestId('widget-remove-one'));
    await clickAsync(screen.getByTestId('home-cancel'));
    expect(order()).toEqual(['widget-one', 'widget-two']);
    expect(stored()).toBeNull();
  });

  it('reorders by dragging a tile onto another', async () => {
    await renderHome();
    await clickAsync(screen.getByTestId('home-customise'));
    expect(screen.getByTestId('widget-one')).not.toHaveAttribute('draggable', 'true');
    expect(screen.getByTestId('widget-handle-one')).toHaveAttribute('draggable', 'true');
    drag(screen.getByTestId('widget-handle-one'), screen.getByTestId('widget-two'));
    expect(order().slice(0, 2)).toEqual(['widget-two', 'widget-one']);
    await clickAsync(screen.getByTestId('home-done'));
    expect(stored().map((i: { id: string }) => i.id)).toEqual(['two', 'one']);
  });

  it('adds by dragging from the library onto the grid or a tile, and removes by dragging back', async () => {
    await renderHome();
    await clickAsync(screen.getByTestId('home-customise'));

    drag(screen.getByTestId('library-opt-in'), screen.getByTestId('widget-two'));
    expect(order()).toEqual(['widget-one', 'widget-opt-in', 'widget-two']);

    drag(screen.getByTestId('widget-handle-one'), screen.getByTestId('home-library'));
    expect(order()).toEqual(['widget-opt-in', 'widget-two']);

    const grid = screen.getByTestId('home-grid');
    drag(screen.getByTestId('library-one'), grid.parentElement!);
    expect(order()).toEqual(['widget-opt-in', 'widget-two', 'widget-one']);
  });

  // jsdom has no PointerEvent, so coordinates travel on a MouseEvent carrying the pointer event's name.
  const pointer = (el: HTMLElement, type: string, clientX: number, clientY: number) =>
    fireEvent(el, new MouseEvent(type, { bubbles: true, clientX, clientY }));

  it('resizes by dragging the bottom-right corner, snapped to the allowed sizes', async () => {
    await renderHome();
    await clickAsync(screen.getByTestId('home-customise'));
    const tile = screen.getByTestId('widget-one');
    // jsdom has no layout: give the grid and the tile the sizes a real browser would report.
    tile.parentElement!.getBoundingClientRect = () => ({ width: 1000 }) as DOMRect;
    tile.getBoundingClientRect = () => ({ height: 280 }) as DOMRect;
    const grip = screen.getByTestId('widget-resize-one');
    pointer(grip, 'pointerdown', 0, 0);
    pointer(grip, 'pointermove', 250, 150);
    expect(tile).toHaveAttribute('data-cols', '3');
    expect(tile).toHaveAttribute('data-rows', '3');
    pointer(grip, 'pointerup', 250, 150);
    pointer(grip, 'pointermove', 900, 900);
    expect(tile).toHaveAttribute('data-cols', '3');
  });

  it('marks widgets already on Home and does not let them be dragged in again', async () => {
    await renderHome();
    await clickAsync(screen.getByTestId('home-customise'));
    const placed = screen.getByTestId('library-one');
    expect(within(placed).getByText(/On Home/)).toBeInTheDocument();
    expect(placed).toHaveAttribute('draggable', 'false');
    expect(screen.getByTestId('library-opt-in')).toHaveAttribute('draggable', 'true');
  });

  it('filters the library by search and category', async () => {
    await renderHome();
    await clickAsync(screen.getByTestId('home-customise'));
    const library = within(screen.getByTestId('home-library'));
    expect(library.getByRole('button', { name: /All/ })).toHaveTextContent('All 3');
    await clickAsync(library.getByRole('button', { name: /demo/ }));
    expect(screen.getByTestId('library-one')).toBeInTheDocument();

    fireEvent.change(screen.getByTestId('library-search'), { target: { value: 'opt' } });
    expect(screen.queryByTestId('library-one')).not.toBeInTheDocument();
    expect(screen.getByTestId('library-opt-in')).toBeInTheDocument();

    fireEvent.change(screen.getByTestId('library-search'), { target: { value: 'zzz' } });
    expect(screen.getByText('No widgets match your search.')).toBeInTheDocument();
  });

  it('restores a saved layout on the next visit; Reset then Done returns to following the default', async () => {
    localStorage.setItem(
      'openjcockpit.homeLayout.v2.fake-user',
      JSON.stringify([{ id: 'two', cols: 5, rows: 2 }]),
    );
    await renderHome();
    expect(order()).toEqual(['widget-two']);

    await clickAsync(screen.getByTestId('home-customise'));
    await clickAsync(screen.getByTestId('home-reset'));
    expect(order()).toEqual(['widget-one', 'widget-two']);
    expect(stored()).toHaveLength(1);
    await clickAsync(screen.getByTestId('home-done'));
    expect(localStorage.getItem('openjcockpit.homeLayout.v2.fake-user')).toBeNull();
  });

  it('disables moving at the ends and has no edit controls outside customise mode', async () => {
    await renderHome();
    expect(screen.queryByTestId('widget-later-one')).not.toBeInTheDocument();
    expect(screen.getByTestId('widget-one')).not.toHaveAttribute('draggable', 'true');
    await clickAsync(screen.getByTestId('home-customise'));
    expect(screen.getByTestId('widget-earlier-one')).toBeDisabled();
    expect(screen.getByTestId('widget-later-two')).toBeDisabled();
  });

  it('explains an empty Home', async () => {
    localStorage.setItem('openjcockpit.homeLayout.v2.fake-user', '[]');
    await renderHome();
    expect(screen.getByTestId('home-empty')).toBeInTheDocument();
  });

  it('greets the user by first name', async () => {
    await renderHome();
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Welcome, Fake');
  });

  it('greets by username when there is no first name', async () => {
    await renderHome([], { id: 'u1', username: 'tony', firstName: '', email: undefined });
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Welcome, tony');
  });

  it('shows a plain welcome and keeps the layout under an anonymous key without a user', async () => {
    await renderHome([], null);
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(/^Welcome$/);
    await clickAsync(screen.getByTestId('home-customise'));
    await clickAsync(screen.getByTestId('widget-remove-one'));
    await clickAsync(screen.getByTestId('home-done'));
    expect(localStorage.getItem('openjcockpit.homeLayout.v2.anonymous')).not.toBeNull();
  });

  it('describes addable widgets', async () => {
    await renderHome();
    await clickAsync(screen.getByTestId('home-customise'));
    expect(screen.getByText('About opt-in')).toBeInTheDocument();
  });
});
