import type { SubagentSpec } from '../../../workflowTypes';
import { DataTable, Button } from '@openjcockpit/ui';

interface SubagentsTableProps {
  items: SubagentSpec[];
  onEdit: (item: SubagentSpec) => void;
  onDelete: (name: string) => void;
}

export function SubagentsTable({ items, onEdit, onDelete }: SubagentsTableProps) {
  return (
    <DataTable>
      <thead>
        <tr>
          <th>Name</th>
          <th>Parent agent</th>
          <th>Skills</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        {items.map((item) => (
          <tr key={item.name}>
            <td>{item.name}</td>
            <td>{item.parentAgent}</td>
            <td>{item.skillNames.join(', ')}</td>
            <td>
              <Button variant="secondary" onClick={() => onEdit(item)}>
                Edit
              </Button>
              <Button variant="secondary" onClick={() => onDelete(item.name)}>
                Delete
              </Button>
            </td>
          </tr>
        ))}
      </tbody>
    </DataTable>
  );
}
