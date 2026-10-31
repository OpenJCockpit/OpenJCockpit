import { fireEvent, screen } from '@testing-library/react';
import { render } from '../../../testUtils/render';
import { useEffect, useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import type { WorkflowGroup } from '@openjcockpit/api';
import { GroupEditor } from './GroupEditor';

const GROUP: WorkflowGroup = { id: 'g1', name: 'Group', description: '', projectName: '' };
let latest: WorkflowGroup;

function Harness({ isNew, initial = GROUP }: { isNew: boolean; initial?: WorkflowGroup }) {
  const [form, setForm] = useState(initial);
  useEffect(() => {
    latest = form;
  });
  return (
    <GroupEditor
      isNew={isNew}
      form={form}
      setForm={setForm}
      saving={false}
      onSave={vi.fn()}
      onCancel={vi.fn()}
    />
  );
}

// Hints render inside the <label>, so match on the label's leading text.
const field = (label: string) => screen.getByLabelText(new RegExp(`^${label}`));

describe('GroupEditor', () => {
  it('titles and locks the id by mode', () => {
    const { unmount } = render(<Harness isNew={false} />);
    expect(screen.getByRole('heading', { name: 'Edit group' })).toBeInTheDocument();
    expect(field('Group ID')).toBeDisabled();
    unmount();
    render(<Harness isNew />);
    expect(screen.getByRole('heading', { name: 'New group' })).toBeInTheDocument();
    expect(field('Group ID')).toBeEnabled();
  });

  it.each([
    ['Group ID', 'id', 'g2'],
    ['Group name', 'name', 'Renamed'],
    ['Project name', 'projectName', 'Proj'],
    ['Description', 'description', 'Desc'],
  ])('updates %s', (label, key, value) => {
    render(<Harness isNew />);
    fireEvent.change(field(label), { target: { value } });
    expect(latest).toMatchObject({ [key]: value });
  });

  it('shows an empty project name for a global group', () => {
    render(<Harness isNew initial={{ ...GROUP, projectName: undefined }} />);
    expect(field('Project name')).toHaveValue('');
  });
});
