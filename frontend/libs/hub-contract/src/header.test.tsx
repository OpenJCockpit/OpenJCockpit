import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { HubHeaderContext, useHubHeader } from './header';

function View() {
  return <main>{useHubHeader() ?? <p>own header</p>}</main>;
}

describe('useHubHeader', () => {
  it('returns the host header', () => {
    render(
      <HubHeaderContext.Provider value={<p>host header</p>}>
        <View />
      </HubHeaderContext.Provider>,
    );
    expect(screen.getByText('host header')).toBeInTheDocument();
  });

  it('is undefined without a host, so the view can fall back', () => {
    render(<View />);
    expect(screen.getByText('own header')).toBeInTheDocument();
  });
});
