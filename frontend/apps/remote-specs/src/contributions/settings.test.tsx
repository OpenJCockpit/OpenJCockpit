import { render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import type { Project } from '@openjcockpit/api';
import { FakeHubProjectProvider } from '@openjcockpit/hub-contract/testing';
import { specsApps } from '../specsApps';

// The libs are stood in for by plain views: these tests cover what this remote contributes.
const provider = ({ children }: { children: ReactNode }) => children;
vi.mock('@openjcockpit/spec-queue', () => ({
  WithSpecQueueTranslations: provider,
  SpecQueueSettings: () => <p>queue settings</p>,
}));
vi.mock('@openjcockpit/workflow', () => ({
  WithWorkflowTranslations: provider,
  OpaSettings: () => <p>opa</p>,
  A2AServicesSettings: () => <p>a2a</p>,
  HermesSettings: ({ projectId }: { projectId: string }) => <p>hermes {projectId}</p>,
  JiraSettings: ({ projectId }: { projectId: string }) => <p>jira {projectId}</p>,
  DocumentFolderSettings: ({ project }: { project: Project }) => <p>documents {project.name}</p>,
}));

const PROJECT = { id: 'p1', name: 'Proj' } as Project;
const sections = specsApps.flatMap((app) => app.settings ?? []);

describe('settings sections of the specs remote', () => {
  it('contributes the workflow integrations and the queue settings with unique ids', () => {
    expect(sections.map((s) => s.id)).toEqual([
      'workflow-opa',
      'workflow-a2a',
      'workflow-hermes',
      'workflow-jira',
      'workflow-documents',
      'spec-queue',
    ]);
    expect(new Set(sections.map((s) => s.id)).size).toBe(sections.length);
  });

  it('flags exactly the project-scoped sections', () => {
    expect(sections.filter((s) => s.requiresProject).map((s) => s.id)).toEqual([
      'workflow-hermes',
      'workflow-jira',
      'workflow-documents',
      'spec-queue',
    ]);
  });

  it('attributes each section to the app that declares it', () => {
    for (const app of specsApps) {
      for (const section of app.settings ?? []) expect(section.sourceAppId).toBe(app.id);
    }
  });

  it.each([
    ['workflow-opa', 'opa'],
    ['workflow-a2a', 'a2a'],
    ['workflow-hermes', 'hermes p1'],
    ['workflow-jira', 'jira p1'],
    ['workflow-documents', 'documents Proj'],
    ['spec-queue', 'queue settings'],
  ])('renders %s content with its translations', async (id, text) => {
    const { default: Section } = await sections.find((s) => s.id === id)!.component();
    render(
      <FakeHubProjectProvider selected={PROJECT}>
        <Section />
      </FakeHubProjectProvider>,
    );
    expect(await screen.findByText(text)).toBeInTheDocument();
  });
});
