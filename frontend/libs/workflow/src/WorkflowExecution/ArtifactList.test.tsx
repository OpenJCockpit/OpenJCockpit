import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ArtifactList, parseArtifactLinks } from './ArtifactList';

describe('parseArtifactLinks', () => {
  it('turns branches into plain labels and pull requests into link entries', () => {
    expect(
      parseArtifactLinks(['git-branch: feature/x ', 'pull-request:https://git.example.com/pr/1']),
    ).toEqual([
      { label: 'Branch: feature/x' },
      { label: 'Pull request', url: 'https://git.example.com/pr/1' },
    ]);
  });

  it('skips blank values and unknown artifact kinds', () => {
    expect(parseArtifactLinks(['git-branch:  ', 'pull-request:', 'file:/tmp/x'])).toEqual([]);
  });
});

describe('ArtifactList', () => {
  it('renders nothing without entries', () => {
    const { container } = render(<ArtifactList entries={[]} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('renders a label as text and a pull request as a safe external link', () => {
    render(
      <ArtifactList
        entries={[{ label: 'Branch: main' }, { label: 'Pull request', url: 'https://x.test/pr/1' }]}
      />,
    );
    expect(screen.getByText('Branch: main').tagName).toBe('SPAN');
    const link = screen.getByRole('link', { name: 'Pull request' });
    expect(link).toHaveAttribute('href', 'https://x.test/pr/1');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
  });
});
