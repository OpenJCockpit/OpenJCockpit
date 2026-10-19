import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import PortalOrb from './PortalOrb';
import type { Portal } from './PortalOrb';

const portal = (id: string): Portal => ({
  id,
  title: `T-${id}`,
  subtitle: `S-${id}`,
  side: 'left',
});

function stubLocation() {
  const location = { href: 'http://localhost:3000/' };
  vi.stubGlobal('location', location);
  return location;
}

afterEach(() => vi.unstubAllGlobals());

describe('PortalOrb', () => {
  it('renders title and subtitle under a portal-specific test id', () => {
    render(<PortalOrb portal={portal('business')} />);

    const orb = screen.getByTestId('portal-orb-business');
    expect(orb).toHaveTextContent('T-business');
    expect(orb).toHaveTextContent('S-business');
  });

  it('opens the dashboard for implementation', () => {
    const location = stubLocation();
    render(<PortalOrb portal={portal('implementation')} />);

    fireEvent.click(screen.getByTestId('portal-orb-implementation'));

    expect(location.href).toBe('http://localhost:4000');
  });

  it('opens the spec files view for business', () => {
    const location = stubLocation();
    render(<PortalOrb portal={portal('business')} />);

    fireEvent.click(screen.getByTestId('portal-orb-business'));

    expect(location.href).toBe('http://localhost:4000/?view=spec-files');
  });

  it('only logs for unknown portals', () => {
    const location = stubLocation();
    const info = vi.spyOn(console, 'info').mockImplementation(() => undefined);
    render(<PortalOrb portal={portal('other')} />);

    fireEvent.click(screen.getByTestId('portal-orb-other'));

    expect(location.href).toBe('http://localhost:3000/');
    expect(info).toHaveBeenCalledWith('T-other opened');
  });
});
