import { useMemo, useState } from 'react';
import type { FormEvent, ReactNode } from 'react';
import {
  IconBox,
  StatusChip,
  AppShell,
  AppHeader,
  Eyebrow,
  Panel,
  DataTable,
  fieldHintClass,
  formInputClass,
  cx,
  Button,
  pageClass,
  pageHeaderClass,
} from '@openjcockpit/ui';
import { useTranslation } from '@openjcockpit/i18n';
import type { StatusKind } from '@openjcockpit/api';
import styles from './SkillsHubDashboard.module.scss';

interface Props {
  onBack: () => void;
  onSettings: () => void;
  /** Replaces the default back-button header, e.g. with the app's shared top menu. */
  header?: ReactNode;
}

type CategoryId = 'dev' | 'ops' | 'security';
type SkillId = 'grill-me' | 'software-quality';

const CATEGORY_KINDS: Record<CategoryId, StatusKind> = {
  dev: 'done',
  ops: 'waiting',
  security: 'active',
};

const SKILL_CATALOG: { id: SkillId; icon: string; categories: CategoryId[] }[] = [
  { id: 'grill-me', icon: '🔥', categories: ['security'] },
  { id: 'software-quality', icon: '✅', categories: ['dev', 'ops'] },
];

export function SkillsHubDashboard({ onBack, onSettings, header }: Props) {
  const [query, setQuery] = useState('');
  const [term, setTerm] = useState('');
  const { t } = useTranslation('skillsHub');

  const skills = useMemo(
    () =>
      SKILL_CATALOG.map((skill) => ({
        id: skill.id,
        icon: skill.icon,
        name: t(`dashboard.skills.${skill.id}.name`),
        description: t(`dashboard.skills.${skill.id}.description`),
        categories: skill.categories.map((category) => ({
          id: category,
          label: t(`dashboard.category.${category}`),
          kind: CATEGORY_KINDS[category],
        })),
      })),
    [t],
  );

  const visibleSkills = useMemo(() => {
    const needle = term.trim().toLowerCase();
    if (!needle) return skills;
    return skills.filter(
      (skill) =>
        skill.name.toLowerCase().includes(needle) ||
        skill.description.toLowerCase().includes(needle) ||
        skill.categories.some((category) => category.label.toLowerCase().includes(needle)),
    );
  }, [skills, term]);

  function handleSearch(e: FormEvent) {
    e.preventDefault();
    setTerm(query);
  }

  return (
    <AppShell>
      {header ?? <AppHeader onBack={onBack} backTitle={t('dashboard.backTitle')} />}

      <main className={pageClass}>
        <div className={cx(pageHeaderClass, styles.skillsHubHeader)}>
          <div>
            <Eyebrow>{t('eyebrow')}</Eyebrow>
            <h1>{t('dashboard.title')}</h1>
          </div>
          <Button variant="secondary" onClick={onSettings} title={t('dashboard.settingsTitle')}>
            {t('dashboard.settings')}
          </Button>
        </div>

        <form className={styles.skillsHubSearch} onSubmit={handleSearch} role="search">
          <input
            className={cx(formInputClass, styles.skillsHubSearchInput)}
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t('dashboard.searchPlaceholder')}
            aria-label={t('dashboard.searchLabel')}
          />
          <Button type="submit" size="sm">
            {t('dashboard.search')}
          </Button>
        </form>

        <Panel as="div" className={styles.skillsHubPanel}>
          <DataTable className={styles.skillsHubTable}>
            <thead>
              <tr>
                <th>{t('dashboard.columnCategory')}</th>
                <th>{t('dashboard.columnSkill')}</th>
              </tr>
            </thead>
            <tbody>
              {visibleSkills.map((skill) => (
                <tr key={skill.id}>
                  <td>
                    <div className={styles.skillCategoryTags}>
                      {skill.categories.map((category) => (
                        <StatusChip key={category.id} label={category.label} kind={category.kind} />
                      ))}
                    </div>
                  </td>
                  <td>
                    <div className={styles.skillRow}>
                      <IconBox label={skill.icon} compact />
                      <div>
                        <strong>{skill.name}</strong>{' '}
                        <span>{skill.description}</span>
                      </div>
                    </div>
                  </td>
                </tr>
              ))}
              {visibleSkills.length === 0 && (
                <tr>
                  <td colSpan={2} className={fieldHintClass}>
                    {t('dashboard.noResults', { term })}
                  </td>
                </tr>
              )}
            </tbody>
          </DataTable>
        </Panel>
      </main>
    </AppShell>
  );
}
