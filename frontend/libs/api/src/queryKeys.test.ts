import { describe, expect, it } from 'vitest';
import { queryKeys } from './queryKeys';

describe('queryKeys', () => {
  it('builds project-scoped keys', () => {
    expect(queryKeys.workspace('c1')).toEqual(['workspace', 'c1']);
    expect(queryKeys.projectSpecs('p1')).toEqual(['project-specs', 'p1']);
    expect(queryKeys.projectSpecFiles('p1')).toEqual(['project-specs', 'p1', 'files']);
    expect(queryKeys.specQueue('p1')).toEqual(['spec-queue', 'p1', 'queue']);
    expect(queryKeys.specQueueSpecFiles('p1')).toEqual(['spec-queue', 'p1', 'spec-files']);
    expect(queryKeys.specQueueSettings('p1')).toEqual(['spec-queue', 'p1', 'settings']);
    expect(queryKeys.jiraConfig('p1')).toEqual(['jira-config', 'p1']);
    expect(queryKeys.hermesConfig('p1')).toEqual(['hermes-config', 'p1']);
    expect(queryKeys.documentFolderConfig('p1')).toEqual(['document-folder-config', 'p1']);
  });

  it('builds workflow keys', () => {
    expect(queryKeys.workflows).toEqual(['workflows']);
    expect(queryKeys.workflowGroups).toEqual(['workflow-groups']);
    expect(queryKeys.linkableAgents).toEqual(['linkable-agents']);
    expect(queryKeys.workflow('w1')).toEqual(['workflow', 'w1']);
  });

  it('keeps a missing project in the key', () => {
    expect(queryKeys.specQueue(null)).toEqual(['spec-queue', null, 'queue']);
  });
});
