import type { ReactNode } from 'react';
import { useSelectedProject } from '@openjcockpit/hub-contract';
import {
  A2AServicesSettings,
  DocumentFolderSettings,
  HermesSettings,
  JiraSettings,
  OpaSettings,
  WithWorkflowTranslations,
} from '@openjcockpit/workflow';

/** Each section registers the workflow namespace itself: the host loads it lazily on its own. */
function Section({ children }: { children: ReactNode }) {
  return <WithWorkflowTranslations>{children}</WithWorkflowTranslations>;
}

export const OpaSection = () => (
  <Section>
    <OpaSettings />
  </Section>
);

export const A2ASection = () => (
  <Section>
    <A2AServicesSettings />
  </Section>
);

// The host renders the project-scoped sections only once a project is selected.
function HermesContent() {
  return <HermesSettings projectId={useSelectedProject().id} />;
}

function JiraContent() {
  return <JiraSettings projectId={useSelectedProject().id} />;
}

function DocumentsContent() {
  return <DocumentFolderSettings project={useSelectedProject()} />;
}

export const HermesSection = () => (
  <Section>
    <HermesContent />
  </Section>
);

export const JiraSection = () => (
  <Section>
    <JiraContent />
  </Section>
);

export const DocumentsSection = () => (
  <Section>
    <DocumentsContent />
  </Section>
);
