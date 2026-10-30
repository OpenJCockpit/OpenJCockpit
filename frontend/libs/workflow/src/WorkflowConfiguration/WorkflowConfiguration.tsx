import type { Project } from '@openjcockpit/api';
import { useTranslation } from '@openjcockpit/i18n';
import { A2AServicesSettings } from './A2AServicesSettings/A2AServicesSettings';
import { DocumentFolderSettings } from './DocumentFolderSettings/DocumentFolderSettings';
import { HermesSettings } from './HermesSettings/HermesSettings';
import { JiraSettings } from './JiraSettings/JiraSettings';
import { OpaSettings } from './OpaSettings/OpaSettings';
import { fieldHintClass } from '@openjcockpit/ui';

interface Props {
  project: Project | null;
}

export function WorkflowConfiguration({ project }: Props) {
  const { t } = useTranslation('workflow');
  return (
    <section className="workflow-configuration">
      <OpaSettings />
      <A2AServicesSettings />
      {project ? (
        <>
          <HermesSettings key={`hermes-${project.id}`} projectId={project.id} />
          <JiraSettings key={`jira-${project.id}`} projectId={project.id} />
          <DocumentFolderSettings key={`folder-${project.id}`} project={project} />
        </>
      ) : (
        <p className={fieldHintClass}>{t('config.selectProject')}</p>
      )}
    </section>
  );
}
