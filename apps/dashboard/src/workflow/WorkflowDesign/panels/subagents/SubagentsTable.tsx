import type { SubagentSpec } from '../../../workflowTypes';

interface SubagentsTableProps {
  items: SubagentSpec[];
  onEdit: (item: SubagentSpec) => void;
  onDelete: (name: string) => void;
}

export function SubagentsTable({ items, onEdit, onDelete }: SubagentsTableProps) {
  return (
    <table className="settings-table">
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
              <button className="back-button" onClick={() => onEdit(item)}>
                Edit
              </button>
              <button className="back-button" onClick={() => onDelete(item.name)}>
                Delete
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
