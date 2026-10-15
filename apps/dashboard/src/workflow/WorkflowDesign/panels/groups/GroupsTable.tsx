import type { WorkflowGroup } from '../../../workflowTypes';
import { DataTable } from '../../../../ui/DataTable/DataTable';

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
              <button className="back-button" onClick={() => onEdit(item)}>
                Edit
              </button>
              <button className="back-button" onClick={() => onDelete(item.id)}>
                Delete
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </DataTable>
  );
}
