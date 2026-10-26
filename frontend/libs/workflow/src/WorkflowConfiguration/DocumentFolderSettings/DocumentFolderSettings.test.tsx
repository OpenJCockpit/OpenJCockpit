import { fireEvent, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '@openjcockpit/api';
import type { Project } from '@openjcockpit/api';
import { clickAsync, renderAsync } from '../../testUtils/dom';
import { DocumentFolderSettings } from './DocumentFolderSettings';
import { basePathOf, joinFolderPath, toCamelCaseFolderName } from './folderPath';

vi.mock('@openjcockpit/api', async (importOriginal) =>
  (await import('../../testUtils/mockApi')).automockApiClients(await importOriginal()),
);

const PROJECT = { id: 'p1', name: 'Absence Pro Solutions' } as Project;
const SAVE = 'Save document folder config';

// Hints and long labels are part of the <label>; match on the leading text.
const field = (label: string) => screen.getByLabelText(new RegExp(`^${label}`));

describe('DocumentFolderSettings', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(console, 'warn').mockImplementation(() => {});
    vi.mocked(api.getDocumentFolderConfig).mockResolvedValue({
      folderName: 'AbsencePro',
      folderPath: '/data/docs/AbsencePro',
      fileTriggerEnabled: true,
      allowedDocumentTypes: 'pdf',
      workflowId: 'wf-1',
    } as never);
    vi.mocked(api.saveDocumentFolderConfig).mockResolvedValue({} as never);
  });

  it('shows loading and then a load error', async () => {
    vi.mocked(api.getDocumentFolderConfig).mockRejectedValue(new Error('x'));
    await renderAsync(<DocumentFolderSettings project={PROJECT} />);
    expect(
      await screen.findByText('Failed to load the document folder configuration.'),
    ).toBeInTheDocument();
  });

  it('derives base path and names from the server config', async () => {
    await renderAsync(<DocumentFolderSettings project={PROJECT} />);
    expect(await screen.findByDisplayValue('/data/docs')).toBeInTheDocument();
    expect(field('Folder name')).toHaveValue('AbsencePro');
    expect(field('Folder location')).toHaveValue('/data/docs/AbsencePro');
    expect(field('File trigger enabled')).toBeChecked();
  });

  it('falls back to defaults and the project name without a stored config', async () => {
    vi.mocked(api.getDocumentFolderConfig).mockResolvedValue({} as never);
    await renderAsync(<DocumentFolderSettings project={PROJECT} />);
    await screen.findByDisplayValue('/documents');
    expect(field('Folder name')).toHaveValue('AbsenceProSolutions');
    expect(field('Folder location')).toHaveValue('/documents/AbsenceProSolutions');
    expect(field('File trigger enabled')).not.toBeChecked();
  });

  it('uses the default base for a root-level stored path', async () => {
    vi.mocked(api.getDocumentFolderConfig).mockResolvedValue({
      folderPath: '/Only',
    } as never);
    await renderAsync(<DocumentFolderSettings project={PROJECT} />);
    expect(await screen.findByDisplayValue('/documents')).toBeInTheDocument();
  });

  it('saves every edited field with the joined folder path', async () => {
    await renderAsync(<DocumentFolderSettings project={PROJECT} />);
    await screen.findByDisplayValue('/data/docs');
    fireEvent.change(field('Folder base location'), { target: { value: '/mnt/' } });
    fireEvent.change(field('Allowed document types'), { target: { value: 'pdf,md' } });
    fireEvent.change(field('Workflow ID to start'), { target: { value: 'wf-2' } });
    fireEvent.click(field('File trigger enabled'));
    expect(field('Folder location')).toHaveValue('/mnt/AbsencePro');
    await clickAsync(screen.getByRole('button', { name: SAVE }));
    expect(api.saveDocumentFolderConfig).toHaveBeenCalledWith('p1', {
      folderPath: '/mnt/AbsencePro',
      fileTriggerEnabled: false,
      allowedDocumentTypes: 'pdf,md',
      workflowId: 'wf-2',
    });
    expect(await screen.findByText('Document folder configuration saved.')).toBeInTheDocument();
  });

  it('keeps the derived read-only fields unchanged when edited', async () => {
    await renderAsync(<DocumentFolderSettings project={PROJECT} />);
    await screen.findByDisplayValue('/data/docs');
    for (const label of ['Project name', 'Folder name', 'Folder location']) {
      expect(field(label)).toBeDisabled();
      fireEvent.change(field(label), { target: { value: 'changed' } });
    }
    expect(field('Project name')).toHaveValue(PROJECT.name);
    expect(field('Folder name')).toHaveValue('AbsencePro');
    expect(field('Folder location')).toHaveValue('/data/docs/AbsencePro');
  });

  it('shows an error when saving fails', async () => {
    vi.mocked(api.saveDocumentFolderConfig).mockRejectedValue(new Error('500'));
    await renderAsync(<DocumentFolderSettings project={PROJECT} />);
    await screen.findByDisplayValue('/data/docs');
    await clickAsync(screen.getByRole('button', { name: SAVE }));
    expect(
      await screen.findByText('Failed to save document folder configuration.'),
    ).toBeInTheDocument();
  });
});

describe('folderPath helpers', () => {
  it('builds a CamelCase folder name', () => {
    expect(toCamelCaseFolderName('absence pro-solutions!')).toBe('AbsenceProSolutions');
    expect(toCamelCaseFolderName('')).toBe('');
  });

  it('extracts the base of a path, ignoring trailing slashes', () => {
    expect(basePathOf('/a/b/c/')).toBe('/a/b');
    expect(basePathOf('/c')).toBe('');
    expect(basePathOf('c')).toBe('');
  });

  it('joins a base and a name, tolerating an empty base', () => {
    expect(joinFolderPath('/a//', 'X')).toBe('/a/X');
    expect(joinFolderPath('', 'X')).toBe('/X');
  });
});
