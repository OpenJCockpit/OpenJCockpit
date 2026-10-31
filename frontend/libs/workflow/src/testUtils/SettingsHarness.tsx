import type { Project } from '@openjcockpit/api';
import { A2AServicesSettings } from '../WorkflowConfiguration/A2AServicesSettings/A2AServicesSettings';
import { DocumentFolderSettings } from '../WorkflowConfiguration/DocumentFolderSettings/DocumentFolderSettings';
import { HermesSettings } from '../WorkflowConfiguration/HermesSettings/HermesSettings';
import { JiraSettings } from '../WorkflowConfiguration/JiraSettings/JiraSettings';
import { OpaSettings } from '../WorkflowConfiguration/OpaSettings/OpaSettings';

/** Every integration section on one page, as the host's settings sections stack them for a project. */
export function WorkflowConfiguration({ project }: { project: Project | null }) {
  return (
    <>
      <OpaSettings />
      <A2AServicesSettings />
      {project && (
        <>
          <HermesSettings projectId={project.id} />
          <JiraSettings projectId={project.id} />
          <DocumentFolderSettings project={project} />
        </>
      )}
    </>
  );
}
