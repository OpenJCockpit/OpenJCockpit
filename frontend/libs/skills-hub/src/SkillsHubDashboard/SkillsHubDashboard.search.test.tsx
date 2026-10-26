import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { SkillsHubDashboard } from './SkillsHubDashboard';

function setup() {
  render(<SkillsHubDashboard onBack={vi.fn()} onSettings={vi.fn()} />);
}

function search(term: string) {
  fireEvent.change(screen.getByLabelText('Search for a skill'), { target: { value: term } });
  fireEvent.click(screen.getByRole('button', { name: '⌕ Search' }));
}

describe('SkillsHubDashboard — search', () => {
  it('lists every skill by default', () => {
    setup();
    expect(screen.getByText('Grill Me')).toBeInTheDocument();
    expect(screen.getByText('Software Quality')).toBeInTheDocument();
  });

  it('only filters on submit, not while typing', () => {
    setup();
    fireEvent.change(screen.getByLabelText('Search for a skill'), { target: { value: 'grill' } });
    expect(screen.getByText('Software Quality')).toBeInTheDocument();
  });

  it('filters by name, case-insensitively and trimmed', () => {
    setup();
    search('  GRILL ');
    expect(screen.getByText('Grill Me')).toBeInTheDocument();
    expect(screen.queryByText('Software Quality')).not.toBeInTheDocument();
  });

  it('filters by description', () => {
    setup();
    search('quality-gate');
    expect(screen.getByText('Software Quality')).toBeInTheDocument();
    expect(screen.queryByText('Grill Me')).not.toBeInTheDocument();
  });

  it('filters by category label', () => {
    setup();
    search('security');
    expect(screen.getByText('Grill Me')).toBeInTheDocument();
    expect(screen.queryByText('Software Quality')).not.toBeInTheDocument();
  });

  it('shows an empty state quoting the search term', () => {
    setup();
    search('zzz');
    expect(screen.getByText('No skills found for "zzz".')).toBeInTheDocument();
  });

  it('restores the full list when the search is cleared', () => {
    setup();
    search('zzz');
    search('');
    expect(screen.getByText('Grill Me')).toBeInTheDocument();
    expect(screen.getByText('Software Quality')).toBeInTheDocument();
  });
});

describe('SkillsHubDashboard — header', () => {
  it('replaces the default header with a custom one', () => {
    render(
      <SkillsHubDashboard onBack={vi.fn()} onSettings={vi.fn()} header={<p>Shared menu</p>} />,
    );
    expect(screen.getByText('Shared menu')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '← Back' })).not.toBeInTheDocument();
  });
});
