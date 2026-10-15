import type { AgentSpec } from '../../../workflowTypes';
import { DataTable } from '../../../../ui/DataTable/DataTable';
import { Button } from '../../../../ui/Button/Button';

interface AgentsTableProps {
  items: AgentSpec[];
  onEdit: (item: AgentSpec) => void;
  onDelete: (name: string) => void;
}

export function AgentsTable({ items, onEdit, onDelete }: AgentsTableProps) {
  return (
    <DataTable>
      <thead>
        <tr>
          <th>Name</th>
          <th>Type</th>
          <th>Role</th>
          <th>Subagents</th>
          <th>Skills</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        {items.map((item) => (
          <tr key={item.name}>
            <td>{item.name}</td>
            <td>{item.builtIn ? 'Built-in' : 'Custom'}</td>
            <td>{item.role}</td>
            <td>{item.subagentNames.join(', ')}</td>
            <td>{item.skillNames.join(', ')}</td>
            <td>
              {!item.builtIn && (
                <>
                  <Button variant="secondary" onClick={() => onEdit(item)}>
                    Edit
                  </Button>
                  <Button variant="secondary" onClick={() => onDelete(item.name)}>
                    Delete
                  </Button>
                </>
              )}
            </td>
          </tr>
        ))}
      </tbody>
    </DataTable>
  );
}
