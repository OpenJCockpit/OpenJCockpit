import type { WorkflowDefinition } from '@openjcockpit/api';
import { useTranslation } from '@openjcockpit/i18n';
import { Actions, DataTable, Button } from '@openjcockpit/ui';

interface WorkflowsTableProps {
  items: WorkflowDefinition[];
  groupName: (groupId?: string) => string;
  onEdit: (item: WorkflowDefinition) => void;
  onDelete: (item: WorkflowDefinition) => void;
}

export function WorkflowsTable({ items, groupName, onEdit, onDelete }: WorkflowsTableProps) {
  const { t } = useTranslation('workflow');
  return (
    <DataTable>
      <thead>
        <tr>
          <th>ID</th>
          <th>{t('shared.name')}</th>
          <th>{t('shared.project')}</th>
          <th>{t('shared.group')}</th>
          <th>{t('shared.status')}</th>
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
