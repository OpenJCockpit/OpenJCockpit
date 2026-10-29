import type { ExternalSkill, SkillSpec } from '@openjcockpit/api';
import { useTranslation } from '@openjcockpit/i18n';
import { DataTable, fieldHintClass, Button } from '@openjcockpit/ui';

type LocalSkillRow = { key: string; readOnly: false; skill: SkillSpec };
type ExternalSkillRow = { key: string; readOnly: true; skill: ExternalSkill };
export type SkillRow = LocalSkillRow | ExternalSkillRow;

interface SkillsTableProps {
  rows: SkillRow[];
  onEdit: (skill: SkillSpec) => void;
  onDelete: (name: string) => void;
}

export function SkillsTable({ rows, onEdit, onDelete }: SkillsTableProps) {
  const { t } = useTranslation('workflow');
  return (
    <DataTable>
      <thead>
        <tr>
          <th>{t('shared.name')}</th>
          <th>{t('skills.marketplace')}</th>
          <th>{t('skills.input')}</th>
          <th>{t('skills.output')}</th>
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
                <span className={fieldHintClass}>{t('skills.notProvided')}</span>
              </td>
              <td>
                <span className={fieldHintClass}>{t('skills.notProvided')}</span>
              </td>
              <td>{t('shared.readOnly')}</td>
            </tr>
          ) : (
            <tr key={row.key}>
              <td>{row.skill.name}</td>
              <td>{t('skills.local')}</td>
              <td>{row.skill.inputContract}</td>
              <td>{row.skill.outputContract}</td>
              <td>
                <Button variant="secondary" onClick={() => onEdit(row.skill)}>
                  {t('shared.edit')}
                </Button>
                <Button variant="secondary" onClick={() => onDelete(row.skill.name)}>
                  {t('shared.delete')}
                </Button>
              </td>
            </tr>
          ),
        )}
      </tbody>
    </DataTable>
  );
}
