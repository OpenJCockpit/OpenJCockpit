import { useMemo, useState } from 'react';
import type { FormEvent } from 'react';
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
import type { StatusKind } from '@openjcockpit/api';
import styles from './SkillsHubDashboard.module.scss';

interface Props {
  onBack: () => void;
  onSettings: () => void;
}

interface SkillCategory {
  label: string;
  kind: StatusKind;
}

interface SkillEntry {
  id: string;
  icon: string;
  name: string;
  description: string;
  categories: SkillCategory[];
}

const CATEGORY_DEV: SkillCategory = { label: 'Dev', kind: 'done' };
const CATEGORY_OPS: SkillCategory = { label: 'Ops', kind: 'waiting' };
const CATEGORY_SECURITY: SkillCategory = { label: 'Security', kind: 'active' };

const SKILLS: SkillEntry[] = [
  {
    id: 'grill-me',
    icon: '🔥',
    name: 'Grill Me',
    description: 'Adversarial review that stress-tests code and design decisions before they ship.',
    categories: [CATEGORY_SECURITY],
  },
  {
    id: 'software-quality',
    icon: '✅',
    name: 'Software Quality',
    description:
      'Automated linting, test coverage, and quality-gate checks across the delivery pipeline.',
    categories: [CATEGORY_DEV, CATEGORY_OPS],
  },
];

export function SkillsHubDashboard({ onBack, onSettings }: Props) {
  const [query, setQuery] = useState('');
  const [term, setTerm] = useState('');

  const visibleSkills = useMemo(() => {
    const needle = term.trim().toLowerCase();
    if (!needle) return SKILLS;
    return SKILLS.filter(
      (skill) =>
        skill.name.toLowerCase().includes(needle) ||
        skill.description.toLowerCase().includes(needle) ||
        skill.categories.some((category) => category.label.toLowerCase().includes(needle)),
    );
  }, [term]);

  function handleSearch(e: FormEvent) {
    e.preventDefault();
    setTerm(query);
  }

  return (
    <AppShell>
      <AppHeader onBack={onBack} backTitle="Back to dashboard">
        <Button variant="secondary" onClick={onSettings} title="Skills Hub settings">
          ⚙ Settings
        </Button>
      </AppHeader>

      <main className={pageClass}>
        <div className={pageHeaderClass}>
          <div>
            <Eyebrow>Skills Hub</Eyebrow>
            <h1>Available skills</h1>
          </div>
        </div>

        <form className={styles.skillsHubSearch} onSubmit={handleSearch} role="search">
          <input
            className={cx(formInputClass, styles.skillsHubSearchInput)}
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search for a skill..."
            aria-label="Search for a skill"
          />
          <Button type="submit" size="sm">
            ⌕ Search
          </Button>
        </form>

        <Panel as="div" className={styles.skillsHubPanel}>
          <DataTable className={styles.skillsHubTable}>
            <thead>
              <tr>
                <th>Category</th>
                <th>Skill</th>
              </tr>
            </thead>
            <tbody>
              {visibleSkills.map((skill) => (
                <tr key={skill.id}>
                  <td>
                    <div className={styles.skillCategoryTags}>
                      {skill.categories.map((category) => (
                        <StatusChip
                          key={category.label}
                          label={category.label}
                          kind={category.kind}
                        />
                      ))}
                    </div>
                  </td>
                  <td>
                    <div className={styles.skillRow}>
                      <IconBox label={skill.icon} compact />
                      <div>
                        <strong>{skill.name}</strong>
                        <span>{skill.description}</span>
                      </div>
                    </div>
                  </td>
                </tr>
              ))}
              {visibleSkills.length === 0 && (
                <tr>
                  <td colSpan={2} className={fieldHintClass}>
                    No skills found for &quot;{term}&quot;.
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
