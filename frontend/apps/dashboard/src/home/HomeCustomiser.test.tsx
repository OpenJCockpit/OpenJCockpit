import { describe, expect, it, vi } from 'vitest';
import { act, createEvent, fireEvent, render, screen, within } from '@testing-library/react';
import { withI18n } from '@openjcockpit/i18n/testing';
import {
  AppRegistryProvider,
  type AppDefinition,
  type WidgetDefinition,
} from '@openjcockpit/hub-contract';
import {
  FakeHubProjectProvider,
  FakeHubSession,
  FakeHubSessionProvider,
} from '@openjcockpit/hub-contract/testing';
import { appResources } from '../i18nResources';
import { HomeCustomiser } from './HomeCustomiser';
import type { LayoutItem } from './homeLayout';

const widget = (id: string, over: Partial<WidgetDefinition> = {}): WidgetDefinition => ({
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
  labelKey: 'Demo app',
  icon: 'x',
  route: '/demo',
  order: 1,
  requiredPermissions: [],
  loadRoutes: () => Promise.resolve([]),
};

const item = (id: string, cols = 2, rows = 2): LayoutItem => ({ id, cols, rows });

async function renderCustomiser(
  widgets: WidgetDefinition[],
  initial: LayoutItem[],
  handlers: { onDone?: () => void; onCancel?: () => void } = {},
) {
  await act(async () => {
    render(
      withI18n(
        <FakeHubSessionProvider session={new FakeHubSession([])}>
          <FakeHubProjectProvider selected={null}>
            <AppRegistryProvider apps={[demo]}>
              <HomeCustomiser
                initial={initial}
                defaultItems={initial}
                widgets={widgets}
                onDone={handlers.onDone ?? vi.fn()}
                onCancel={handlers.onCancel ?? vi.fn()}
              />
            </AppRegistryProvider>
          </FakeHubProjectProvider>
        </FakeHubSessionProvider>,
        appResources,
      ),
    );
  });
}

const dataTransfer = {
  effectAllowed: '',
  dropEffect: '',
  setData: () => undefined,
  setDragImage: () => undefined,
};

describe('HomeCustomiser library', () => {
  const widgets = [
    widget('compact', { cols: 1 }),
    widget('standard'),
    widget('wide', { cols: 4, categoryKey: 'Custom category' }),
    widget('full', { cols: 6, sourceAppId: 'ghost' }),
  ];

  it('describes each widget by size and category, naming unknown apps by their id', async () => {
    await renderCustomiser(widgets, []);
    expect(screen.getByTestId('library-compact')).toHaveTextContent('Demo app · Compact');
    expect(screen.getByTestId('library-standard')).toHaveTextContent('Demo app · Standard');
    expect(screen.getByTestId('library-wide')).toHaveTextContent('Custom category · Wide');
    expect(screen.getByTestId('library-full')).toHaveTextContent('ghost · Full width');
  });

  it('toggles a category filter and clears it with All', async () => {
    await renderCustomiser(widgets, []);
    const library = within(screen.getByTestId('home-library'));
    fireEvent.click(library.getByRole('button', { name: /^ghost/ }));
    expect(screen.queryByTestId('library-compact')).not.toBeInTheDocument();
    expect(screen.getByTestId('library-full')).toBeInTheDocument();
    fireEvent.click(library.getByRole('button', { name: /^ghost/ }));
    expect(screen.getByTestId('library-compact')).toBeInTheDocument();
    fireEvent.click(library.getByRole('button', { name: /^ghost/ }));
    fireEvent.click(library.getByRole('button', { name: /^All/ }));
    expect(screen.getByTestId('library-compact')).toBeInTheDocument();
  });

  it('highlights the library only while a placed tile is dragged over it', async () => {
    await renderCustomiser(widgets, [item('compact')]);
    const library = screen.getByTestId('home-library');
    fireEvent.dragEnter(library, { dataTransfer });
    // jsdom drops `relatedTarget` from drag events, so it is attached by hand.
    const leaveTo = (relatedTarget: Element | null) => {
      const event = createEvent.dragLeave(library, { dataTransfer });
      Object.defineProperty(event, 'relatedTarget', { value: relatedTarget });
      fireEvent(library, event);
    };
    leaveTo(screen.getByTestId('library-search'));
    leaveTo(null);
    leaveTo(library.querySelector('strong'));
    expect(library).toBeInTheDocument();
  });

  it('ignores drops that carry no drag of ours', async () => {
    await renderCustomiser(widgets, [item('compact')]);
    fireEvent.drop(screen.getByTestId('home-library'), { dataTransfer });
    fireEvent.drop(screen.getByTestId('home-grid').parentElement!, { dataTransfer });
    fireEvent.dragEnter(screen.getByTestId('widget-compact'), { dataTransfer });
    fireEvent.drop(screen.getByTestId('widget-compact'), { dataTransfer });
    expect(screen.getByTestId('widget-compact')).toBeInTheDocument();
  });

  it('does not start a drag for a widget already on Home', async () => {
    await renderCustomiser(widgets, [item('compact')]);
    const placed = screen.getByTestId('library-compact');
    fireEvent.dragStart(placed, { dataTransfer });
    fireEvent.drop(screen.getByTestId('home-grid').parentElement!, { dataTransfer });
    expect(screen.getAllByTestId('widget-compact')).toHaveLength(1);
  });

  it('does not add a library drag onto a tile of the grid more than once, and ignores tile drops on the grid', async () => {
    await renderCustomiser(widgets, [item('compact'), item('standard')]);
    const handle = screen.getByTestId('widget-handle-compact');
    fireEvent.dragStart(handle, { dataTransfer });
    fireEvent.drop(screen.getByTestId('home-grid').parentElement!, { dataTransfer });
    fireEvent.dragEnd(handle, { dataTransfer });
    expect(
      within(screen.getByTestId('home-grid'))
        .getAllByRole('article')
        .map((a) => a.dataset.testid),
    ).toEqual(['widget-compact', 'widget-standard']);
  });
});

describe('HomeCustomiser layout', () => {
  const widgets = [widget('a'), widget('b')];

  it('skips layout entries whose widget no longer exists', async () => {
    await renderCustomiser(widgets, [item('a'), item('gone')]);
    expect(screen.getAllByRole('article')).toHaveLength(1);
  });

  it('ignores keys that do not resize', async () => {
    await renderCustomiser(widgets, [item('a')]);
    fireEvent.keyDown(screen.getByTestId('widget-resize-a'), { key: 'Enter' });
    expect(screen.getByTestId('widget-a')).toHaveAttribute('data-cols', '2');
  });

  it('shrinks with the arrow keys down to the widget minimum', async () => {
    await renderCustomiser([widget('a', { minCols: 1, minRows: 1 })], [item('a')]);
    const grip = screen.getByTestId('widget-resize-a');
    fireEvent.keyDown(grip, { key: 'ArrowLeft' });
    expect(screen.getByTestId('widget-a')).toHaveAttribute('data-cols', '1');
    fireEvent.keyDown(grip, { key: 'ArrowUp' });
    expect(screen.getByTestId('widget-a')).toHaveAttribute('data-rows', '1');
  });

  it('resizes by pointer in a layout-less environment', async () => {
    await renderCustomiser(widgets, [item('a')]);
    const grip = screen.getByTestId('widget-resize-a');
    fireEvent(grip, new MouseEvent('pointerdown', { bubbles: true, clientX: 0, clientY: 0 }));
    fireEvent(grip, new MouseEvent('pointermove', { bubbles: true, clientX: 5, clientY: 5 }));
    expect(screen.getByTestId('widget-a')).toBeInTheDocument();
  });

  it('ignores pointer movement when no resize has started', async () => {
    await renderCustomiser(widgets, [item('a')]);
    const grip = screen.getByTestId('widget-resize-a');
    fireEvent(grip, new MouseEvent('pointermove', { bubbles: true, clientX: 50, clientY: 50 }));
    expect(screen.getByTestId('widget-a')).toHaveAttribute('data-cols', '2');
  });
});
