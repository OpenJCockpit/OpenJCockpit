import { fireEvent, render as rtlRender, screen, waitFor, within } from '@testing-library/react';
import {
  createI18n,
  I18nProvider,
  LOCALE_STORAGE_KEY,
  type LocaleLoader,
} from '@openjcockpit/i18n';
import { render } from '../../testUtils/render';
import { MemoryRouter, useLocation } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { dashboardResources } from '../../i18n';
import nl from '../../locales/nl.json';
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

function renderWithI18nInstance(i18n: ReturnType<typeof createI18n>, loader: LocaleLoader) {
  rtlRender(
    <I18nProvider i18n={i18n} loader={loader}>
      <MemoryRouter>
        <SiteHeader
          active="platform"
          projects={[]}
          selectedProject={null}
          onProjectChange={vi.fn()}
          onLogout={vi.fn()}
        />
      </MemoryRouter>
    </I18nProvider>,
  );
}

function chooseLanguage(name: string) {
  fireEvent.click(screen.getByRole('combobox', { name: 'Language' }));
  fireEvent.click(screen.getByRole('option', { name }));
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

  it('collapses to icons only and remembers the choice', () => {
    window.localStorage.clear();
    setup({ projects: [project('a', 'Alpha')] });
    expect(screen.getByRole('combobox', { name: 'Active project' })).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('nav-collapse'));
    expect(screen.getByTestId('nav-collapse')).toHaveAttribute('aria-label', 'Expand');
    expect(screen.queryByRole('combobox', { name: 'Active project' })).not.toBeInTheDocument();
    expect(screen.getByTestId('nav-platform')).toHaveAttribute('title', 'Platform');
    expect(window.localStorage.getItem('openjcockpit.sidebarCollapsed')).toBe('1');
    fireEvent.click(screen.getByTestId('nav-collapse'));
    expect(screen.getByRole('combobox', { name: 'Active project' })).toBeInTheDocument();
  });

  it('logs out', () => {
    const { onLogout } = setup();
    fireEvent.click(screen.getByTestId('nav-logout'));
    expect(onLogout).toHaveBeenCalledTimes(1);
  });

  it('hides the project picker when there are no projects', () => {
    setup();
    expect(screen.queryByRole('combobox', { name: 'Active project' })).not.toBeInTheDocument();
  });

  it('offers every supported language by its own name', () => {
    setup();
    const select = screen.getByRole('combobox', { name: 'Language' });
    expect(select).toHaveTextContent('English');
    fireEvent.click(select);
    expect(
      within(screen.getByRole('listbox')).getByRole('option', { name: 'Nederlands' }),
    ).toHaveAttribute('lang', 'nl');
  });

  it('switches the language, translating the header and remembering the choice', async () => {
    window.localStorage.clear();
    const i18n = createI18n({ resources: dashboardResources });
    const loader = vi.fn().mockResolvedValue({ nl });
    renderWithI18nInstance(i18n, loader);
    chooseLanguage('Nederlands');
    expect(await screen.findByRole('navigation', { name: 'Hoofdnavigatie' })).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByTestId('locale-select')).toHaveTextContent('Nederlands'),
    );
    expect(document.documentElement.lang).toBe('nl');
    expect(window.localStorage.getItem(LOCALE_STORAGE_KEY)).toBe('nl');
  });

  it('stays on the current language when the translations cannot be downloaded', async () => {
    const i18n = createI18n({ resources: dashboardResources });
    const loader = vi.fn().mockRejectedValue(new Error('offline'));
    renderWithI18nInstance(i18n, loader);
    chooseLanguage('Nederlands');
    await waitFor(() => expect(loader).toHaveBeenCalled());
    expect(screen.getByTestId('locale-select')).toHaveTextContent('English');
  });

  it('shows the selected project and reports changes', () => {
    const a = project('a', 'Alpha');
    const b = project('b', 'Beta');
    const { onProjectChange } = setup({ projects: [a, b], selectedProject: b });
    const select = screen.getByRole('combobox', { name: 'Active project' });
    expect(select).toHaveTextContent('Beta');
    fireEvent.click(select);
    fireEvent.click(screen.getByRole('option', { name: 'Alpha' }));
    expect(onProjectChange).toHaveBeenCalledWith('a');
  });
});
