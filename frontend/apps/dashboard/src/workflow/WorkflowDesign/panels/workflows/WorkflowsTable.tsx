import type { WorkflowDefinition } from '@openjcockpit/api';
import { DataTable, Button } from '@openjcockpit/ui';

interface WorkflowsTableProps {
  items: WorkflowDefinition[];
  groupName: (groupId?: string) => string;
  onEdit: (item: WorkflowDefinition) => void;
  onDelete: (id: string) => void;
}

export function WorkflowsTable({ items, groupName, onEdit, onDelete }: WorkflowsTableProps) {
  return (
    <DataTable>
      <thead>
        <tr>
          <th>ID</th>
          <th>Name</th>
          <th>Project</th>
          <th>Group</th>
          <th>Status</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        {items.map((item) => (
          <tr key={item.id}>
            <td>{item.id}</td>
            <td>{item.name}</td>
            <td>{item.projectName}</td>
            <td>{groupName(item.groupId)}</td>
            <td>{item.status}</td>
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
