import './SkillsHubDashboard.scss';
import { useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { IconBox } from '../IconBox/IconBox';
import { StatusChip } from '../StatusChip/StatusChip';
import type { StatusKind } from '../../types';

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
    <div className="app-shell">
      <div className="neural-bg" aria-hidden="true" />
      <div className="orb orb--left" aria-hidden="true" />
      <div className="orb orb--right" aria-hidden="true" />

      <header className="site-header">
        <div className="brand">
          <img src="/openjcockpit-logo.png" alt="OpenJCockpit" className="brand-logo" />
        </div>
        <div className="header-actions">
          <button className="back-button" onClick={onSettings} title="Skills Hub settings">
            ⚙ Settings
          </button>
          <button className="back-button" onClick={onBack} title="Back to dashboard">
            ← Back
          </button>
        </div>
      </header>

      <main className="spec-files-dashboard">
        <div className="spec-files-dashboard-header">
          <div>
            <span className="eyebrow">Skills Hub</span>
            <h1>Available skills</h1>
          </div>
        </div>

        <form className="skills-hub-search" onSubmit={handleSearch} role="search">
          <input
            className="settings-input skills-hub-search__input"
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search for a skill..."
            aria-label="Search for a skill"
          />
          <button type="submit" className="button button--small">
            ⌕ Search
          </button>
        </form>

        <div className="panel skills-hub-panel">
          <table className="settings-table skills-hub-table">
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
                    <div className="skill-category-tags">
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
                    <div className="skill-row">
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
                  <td colSpan={2} className="settings-desc">
                    No skills found for &quot;{term}&quot;.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </main>
    </div>
  );
}
