import type { SubagentSpec } from '@openjcockpit/api';
import { useTranslation } from '@openjcockpit/i18n';
import { DataTable, Button } from '@openjcockpit/ui';

interface SubagentsTableProps {
  items: SubagentSpec[];
  onEdit: (item: SubagentSpec) => void;
  onDelete: (name: string) => void;
}

export function SubagentsTable({ items, onEdit, onDelete }: SubagentsTableProps) {
  const { t } = useTranslation('workflow');
  return (
    <DataTable>
      <thead>
        <tr>
          <th>{t('shared.name')}</th>
          <th>{t('subagents.parentAgent')}</th>
          <th>{t('shared.skills')}</th>
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
                {t('shared.edit')}
              </Button>
              <Button variant="secondary" onClick={() => onDelete(item.name)}>
                {t('shared.delete')}
              </Button>
            </td>
          </tr>
        ))}
      </tbody>
    </DataTable>
  );
}
