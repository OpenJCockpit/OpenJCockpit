import type { WorkflowDefinition } from '../../../workflowTypes';
import { DataTable } from '../../../../ui/DataTable/DataTable';

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
