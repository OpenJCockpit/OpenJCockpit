import { Component, Suspense, use, useEffect, type ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { useTranslation } from '@openjcockpit/i18n';
import { useHubProject, type SettingsSectionDefinition } from '@openjcockpit/hub-contract';
import { Button, LoadingState, Panel, StatusMessage } from '@openjcockpit/ui';
import { APP_TITLE } from '../appTitle';
import { ROUTES } from '../routes';
import styles from './SettingsLayout.module.scss';

/** A section that throws must not take the sub-navigation down with it. */
class SectionBoundary extends Component<
  { message: string; children: ReactNode },
  { failed: boolean }
> {
  override state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  override componentDidCatch(error: Error) {
    console.error('A settings section failed to render', error);
  }

  override render() {
    return this.state.failed ? (
      <StatusMessage variant="error" role="alert">
        {this.props.message}
      </StatusMessage>
    ) : (
      this.props.children
    );
  }
}

// One load per definition for the page's lifetime; the promise is cached so re-renders do not refetch.
const loads = new WeakMap<
  SettingsSectionDefinition,
  ReturnType<SettingsSectionDefinition['component']>
>();

function loadOnce(section: SettingsSectionDefinition) {
  let load = loads.get(section);
  if (!load) {
    load = section.component();
    loads.set(section, load);
  }
  return load;
}

function Loaded({ section }: { section: SettingsSectionDefinition }) {
  const { default: Section } = use(loadOnce(section));
  return <Section />;
}

/** Owns what is common to every section: heading, description, project guard and failure isolation. */
export function SectionFrame({ section }: { section: SettingsSectionDefinition }) {
  const { t } = useTranslation('dashboard');
  const { t: tCommon } = useTranslation();
  const navigate = useNavigate();
  const { selected } = useHubProject();
  const title = String(t(section.titleKey as never));
  const description = section.descriptionKey
    ? String(t(section.descriptionKey as never))
    : undefined;
  const heading = t(
    section.screen === 'project' ? 'pageTitle.projectSettings' : 'pageTitle.settings',
  );
  useEffect(() => {
    document.title = `${heading} · ${title} · ${APP_TITLE}`;
  }, [heading, title]);
  const needsProject = section.requiresProject && !selected;

  return (
    <section className={styles.section} aria-labelledby={`settings-title-${section.id}`}>
      <header className={styles.sectionHeader}>
        <h1 id={`settings-title-${section.id}`}>{title}</h1>
        {description && <p>{description}</p>}
      </header>
      <Panel className={styles.card} data-testid={`settings-card-${section.id}`}>
        {needsProject ? (
          <div data-testid="settings-select-project">
            <StatusMessage>{t('settings.selectProject')}</StatusMessage>
            <Button variant="primary" onClick={() => void navigate(ROUTES.projects)}>
              {t('settings.chooseProject')}
            </Button>
          </div>
        ) : (
          // A project switch re-mounts the section so its forms reset.
          <SectionBoundary key={selected?.id ?? ''} message={tCommon('somethingWrong')}>
            {/* Not `ViewLoading`: that is a whole page (shell and sidebar), and this is a card. */}
            <Suspense fallback={<LoadingState>{t('loadingView')}</LoadingState>}>
              <Loaded section={section} />
            </Suspense>
          </SectionBoundary>
        )}
      </Panel>
    </section>
  );
}
