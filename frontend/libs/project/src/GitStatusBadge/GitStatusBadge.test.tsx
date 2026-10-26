import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { GitStatus } from '@openjcockpit/api';
import { GitStatusBadge } from './GitStatusBadge';

describe('GitStatusBadge', () => {
  it.each<[GitStatus, string]>([
    ['ACCESSIBLE', 'Reachable'],
    ['NOT_ACCESSIBLE', 'Not reachable'],
    ['CHECK_FAILED', 'Check failed'],
    ['UNKNOWN', 'Unknown'],
  ])('shows the %s label', (status, label) => {
    render(<GitStatusBadge status={status} />);
    expect(screen.getByLabelText(label)).toHaveTextContent(label);
  });

  it.each<[GitStatus, string]>([
    ['ACCESSIBLE', 'Git reachable'],
    ['NOT_ACCESSIBLE', 'Git not reachable'],
    ['CHECK_FAILED', 'Git not reachable'],
  ])('renders an icon-only badge for %s when compact', (status, label) => {
    render(<GitStatusBadge status={status} compact />);
    expect(screen.getByLabelText(label)).toBeInTheDocument();
  });

  it('renders nothing for UNKNOWN when compact', () => {
    const { container } = render(<GitStatusBadge status="UNKNOWN" compact />);
    expect(container).toBeEmptyDOMElement();
  });
});
