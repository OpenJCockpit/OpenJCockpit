import type { WorkflowGroup } from '@openjcockpit/api';
import { useTranslation } from '@openjcockpit/i18n';
import { Actions, DataTable, Button } from '@openjcockpit/ui';

interface GroupsTableProps {
  items: WorkflowGroup[];
  onEdit: (item: WorkflowGroup) => void;
  onDelete: (item: WorkflowGroup) => void;
}

export function GroupsTable({ items, onEdit, onDelete }: GroupsTableProps) {
  const { t } = useTranslation('workflow');
  return (
    <DataTable>
      <thead>
        <tr>
          <th>ID</th>
          <th>{t('shared.name')}</th>
          <th>{t('shared.project')}</th>
          <th>{t('shared.description')}</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        {items.map((item) => (
          <tr key={item.id}>
            <td>{item.id}</td>
            <td>{item.name}</td>
            <td>{item.projectName || t('groups.global')}</td>
            <td>{item.description}</td>
            <td>
              <Actions>
                <Button variant="secondary" onClick={() => onEdit(item)}>
                  {t('shared.edit')}
                </Button>
                <Button variant="danger" onClick={() => onDelete(item)}>
                  {t('shared.delete')}
                </Button>
              </Actions>
            </td>
          </tr>
        ))}
      </tbody>
    </DataTable>
  );
}
