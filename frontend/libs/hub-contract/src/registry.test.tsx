import { describe, expect, it, vi } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import { useSyncExternalStore } from 'react';
import type { AppDefinition, SettingsSectionDefinition } from './appDefinition';
import { AppRegistryProvider, useAppRegistry, useBadgeFor } from './registry';
import { FakeHubSession, FakeHubSessionProvider } from './testing';

const lazy = () => Promise.resolve({ default: () => null });

function app(id: string, over: Partial<AppDefinition> = {}): AppDefinition {
  return {
    id,
    labelKey: id,
    icon: 'x',
    route: `/${id}`,
    order: 10,
    requiredPermissions: [],
    loadRoutes: () => Promise.resolve([]),
    ...over,
  };
}

function Probe({ pick }: { pick: (r: ReturnType<typeof useAppRegistry>) => string }) {
  return <p data-testid="out">{pick(useAppRegistry())}</p>;
}

function renderRegistry(apps: AppDefinition[], permissions: string[], ui: React.ReactNode) {
  return render(
    <FakeHubSessionProvider session={new FakeHubSession(permissions)}>
      <AppRegistryProvider apps={apps}>{ui}</AppRegistryProvider>
    </FakeHubSessionProvider>,
  );
}

const ids = (items: readonly { id: string }[]) => items.map((i) => i.id).join(',');

describe('AppRegistryProvider', () => {
  it('sorts by order and filters by permission', () => {
    const apps = [
      app('b', { order: 2 }),
      app('a', { order: 1 }),
      app('c', { order: 0, requiredPermissions: ['x'] }),
    ];
    renderRegistry(apps, [], <Probe pick={(r) => `${ids(r.allApps)}|${ids(r.visibleApps)}`} />);
    expect(screen.getByTestId('out')).toHaveTextContent('c,a,b|a,b');
  });

  it('hides showInNav false apps from the nav only', () => {
    const apps = [app('a'), app('b', { showInNav: false })];
    renderRegistry(apps, [], <Probe pick={(r) => `${ids(r.navApps)}|${ids(r.visibleApps)}`} />);
    expect(screen.getByTestId('out')).toHaveTextContent('a|a,b');
  });

  it('drops items of a hidden app and checks each item permission', () => {
    const apps = [
      app('hidden', {
        requiredPermissions: ['nope'],
        widgets: [
          {
            id: 'w-hidden',
            titleKey: 'w',
            sourceAppId: 'hidden',
            requiredPermissions: [],
            component: lazy,
            defaultFor: [],
            link: '/hidden',
          },
        ],
      }),
      app('shown', {
        widgets: [
          {
            id: 'w-ok',
            titleKey: 'w',
            sourceAppId: 'shown',
            requiredPermissions: [],
            component: lazy,
            defaultFor: [],
            link: '/shown',
          },
          {
            id: 'w-no',
            titleKey: 'w',
            sourceAppId: 'shown',
            requiredPermissions: ['nope'],
            component: lazy,
            defaultFor: [],
            link: '/shown',
          },
        ],
      }),
    ];
    renderRegistry(apps, [], <Probe pick={(r) => ids(r.visibleWidgets)} />);
    expect(screen.getByTestId('out')).toHaveTextContent(/^w-ok$/);
  });

  it('lists permitted settings sections by order then id, skipping duplicates and hidden apps', () => {
    const section = (id: string, order: number, over: Partial<SettingsSectionDefinition> = {}) => ({
      id,
      sourceAppId: 'x',
      titleKey: id,
      requiredPermissions: [],
      order,
      component: lazy,
      ...over,
    });
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    const apps = [
      app('a', {
        order: 1,
        settings: [
          section('b', 20),
          section('a2', 20),
          section('locked', 5, { requiredPermissions: ['nope'] }),
        ],
      }),
      app('b', { order: 2, settings: [section('first', 10), section('b', 1)] }),
      app('hidden', { order: 3, requiredPermissions: ['nope'], settings: [section('h', 1)] }),
    ];
    renderRegistry(apps, [], <Probe pick={(r) => ids(r.visibleSettings)} />);
    expect(screen.getByTestId('out')).toHaveTextContent(/^first,a2,b$/);
    expect(warn).toHaveBeenCalledWith(expect.stringContaining('"b"'));
    warn.mockRestore();
  });

  it('publishes badges from useBadge and re-renders on change', () => {
    let count: number | null = 3;
    const listeners = new Set<() => void>();
    const subscribe = (l: () => void) => {
      listeners.add(l);
      return () => listeners.delete(l);
    };
    const useBadge = () =>
      useSyncExternalStore(
        subscribe,
        () => count,
        () => count,
      );
    function Badge() {
      return <p data-testid="badge">{String(useBadgeFor('a'))}</p>;
    }
    renderRegistry([app('a', { useBadge })], [], <Badge />);
    expect(screen.getByTestId('badge')).toHaveTextContent('3');
    count = 5;
    act(() => listeners.forEach((l) => l()));
    expect(screen.getByTestId('badge')).toHaveTextContent('5');
  });

  it('clears the badge when the app becomes hidden', () => {
    const badged = app('a', { requiredPermissions: ['x'], useBadge: () => 2 });
    const session = new FakeHubSession(['x']);
    function Badge() {
      return <p data-testid="badge">{String(useBadgeFor('a'))}</p>;
    }
    const ui = (
      <FakeHubSessionProvider session={session}>
        <AppRegistryProvider apps={[badged]}>
          <Badge />
        </AppRegistryProvider>
      </FakeHubSessionProvider>
    );
    const { rerender } = render(ui);
    expect(screen.getByTestId('badge')).toHaveTextContent('2');
    const revoked = new FakeHubSession([]);
    rerender(
      <FakeHubSessionProvider session={revoked}>
        <AppRegistryProvider apps={[badged]}>
          <Badge />
        </AppRegistryProvider>
      </FakeHubSessionProvider>,
    );
    expect(screen.getByTestId('badge')).toHaveTextContent('null');
  });

  it('throws without a provider', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    expect(() => render(<Probe pick={() => ''} />)).toThrow('AppRegistryProvider');
  });
});
