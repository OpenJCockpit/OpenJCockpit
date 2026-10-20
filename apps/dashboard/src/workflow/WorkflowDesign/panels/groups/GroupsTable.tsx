import type { WorkflowGroup } from '../../../workflowTypes';
import { DataTable, Button } from '@openjcockpit/ui';

interface GroupsTableProps {
  items: WorkflowGroup[];
  onEdit: (item: WorkflowGroup) => void;
  onDelete: (id: string) => void;
}

export function GroupsTable({ items, onEdit, onDelete }: GroupsTableProps) {
  return (
    <DataTable>
      <thead>
        <tr>
          <th>ID</th>
          <th>Name</th>
          <th>Project</th>
          <th>Description</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        {items.map((item) => (
          <tr key={item.id}>
            <td>{item.id}</td>
            <td>{item.name}</td>
            <td>{item.projectName || 'Global (all projects)'}</td>
            <td>{item.description}</td>
            <td>
              <Button variant="secondary" onClick={() => onEdit(item)}>
                Edit
              </Button>
              <Button variant="secondary" onClick={() => onDelete(item.id)}>
                Delete
              </Button>
            </td>
          </tr>
        ))}
      </tbody>
    </DataTable>
  );
}
