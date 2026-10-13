import type { AgentSpec } from '../../../workflowTypes';

interface AgentsTableProps {
  items: AgentSpec[];
  onEdit: (item: AgentSpec) => void;
  onDelete: (name: string) => void;
}

export function AgentsTable({ items, onEdit, onDelete }: AgentsTableProps) {
  return (
    <table className="settings-table">
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
                  <button className="back-button" onClick={() => onEdit(item)}>
                    Edit
                  </button>
                  <button className="back-button" onClick={() => onDelete(item.name)}>
                    Delete
                  </button>
                </>
              )}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
