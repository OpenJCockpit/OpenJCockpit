import type { ExternalSkill, SkillSpec } from '../../../workflowTypes';

type LocalSkillRow = { key: string; readOnly: false; skill: SkillSpec };
type ExternalSkillRow = { key: string; readOnly: true; skill: ExternalSkill };
export type SkillRow = LocalSkillRow | ExternalSkillRow;

interface SkillsTableProps {
  rows: SkillRow[];
  onEdit: (skill: SkillSpec) => void;
  onDelete: (name: string) => void;
}

export function SkillsTable({ rows, onEdit, onDelete }: SkillsTableProps) {
  return (
    <table className="settings-table">
      <thead>
        <tr>
          <th>Name</th>
          <th>Marketplace</th>
          <th>Input</th>
          <th>Output</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row) =>
          row.readOnly ? (
            <tr key={row.key}>
              <td>{row.skill.name}</td>
              <td>{row.skill.marketplaceName}</td>
              <td>
                <span className="settings-desc">Not provided</span>
              </td>
              <td>
                <span className="settings-desc">Not provided</span>
              </td>
              <td>Read-only</td>
            </tr>
          ) : (
            <tr key={row.key}>
              <td>{row.skill.name}</td>
              <td>Local</td>
              <td>{row.skill.inputContract}</td>
              <td>{row.skill.outputContract}</td>
              <td>
                <button className="back-button" onClick={() => onEdit(row.skill)}>
                  Edit
                </button>
                <button className="back-button" onClick={() => onDelete(row.skill.name)}>
                  Delete
                </button>
              </td>
            </tr>
          ),
        )}
      </tbody>
    </table>
  );
}
