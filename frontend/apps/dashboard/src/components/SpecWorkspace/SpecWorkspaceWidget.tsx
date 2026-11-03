import { useHubProject } from '@openjcockpit/hub-contract';
import { useTranslation } from '@openjcockpit/i18n';
import { SpecWorkspace } from './SpecWorkspace';

/** Home widget: the dashboard's spec workspace for the selected project. */
export default function SpecWorkspaceWidget() {
  const { t } = useTranslation('dashboard');
  const { selected } = useHubProject();
  if (!selected) return <p data-testid="spec-workspace-no-project">{t('home.selectProject')}</p>;
  return <SpecWorkspace key={selected.id} project={selected} />;
}
