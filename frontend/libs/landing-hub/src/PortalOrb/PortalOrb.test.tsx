import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { PortalOrb } from './PortalOrb';
import type { Portal, PortalId } from './PortalOrb';

const portal = (id: PortalId): Portal => ({
  id,
  title: `T-${id}`,
  subtitle: `S-${id}`,
  side: 'left',
});

describe('PortalOrb', () => {
  it('renders title and subtitle under a portal-specific test id', () => {
    render(<PortalOrb portal={portal('business')} onSelect={() => undefined} />);

    const orb = screen.getByTestId('portal-orb-business');
    expect(orb).toHaveTextContent('T-business');
    expect(orb).toHaveTextContent('S-business');
  });

  it.each<PortalId>(['business', 'implementation'])('reports %s when clicked', (id) => {
    const onSelect = vi.fn();
    render(<PortalOrb portal={portal(id)} onSelect={onSelect} />);

    fireEvent.click(screen.getByTestId(`portal-orb-${id}`));

    expect(onSelect).toHaveBeenCalledExactlyOnceWith(id);
  });
});
