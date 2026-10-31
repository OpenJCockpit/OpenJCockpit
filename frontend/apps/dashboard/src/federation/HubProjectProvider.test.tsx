import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { Project } from '@openjcockpit/api';
import { useHubProject } from '@openjcockpit/hub-contract';
import { HubProjectProvider } from './HubProjectProvider';

const alpha = { id: 'a', name: 'Alpha' } as Project;

function Probe() {
  const { projects, selected, select } = useHubProject();
  return (
    <button type="button" onClick={() => select('a')}>
      {projects.length} {selected?.name}
    </button>
  );
}

describe('HubProjectProvider', () => {
  it('gives remotes the host projects and selection and forwards selections', () => {
    const onSelect = vi.fn();
    render(
      <HubProjectProvider projects={[alpha]} selected={alpha} onSelect={onSelect}>
        <Probe />
      </HubProjectProvider>,
    );
    fireEvent.click(screen.getByRole('button', { name: '1 Alpha' }));
    expect(onSelect).toHaveBeenCalledWith('a');
  });
});
