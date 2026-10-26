import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, setAuthToken } from '../core/http';
import {
  activateProject,
  createProject,
  deactivateProject,
  deleteGitCredentials,
  getGitCredentials,
  getGitStatus,
  loadAllProjects,
  loadProjects,
  patchNewProject,
  saveGitCredentials,
  triggerGitCheck,
  updateProject,
} from './projectsApi';
import type { ProjectGitCredentialRequest, ProjectRequest } from '../types/types';

const mockFetch = vi.fn();

function ok(body?: unknown) {
  return { ok: true, status: 200, json: () => Promise.resolve(body) };
}
function fail(status: number, body?: unknown) {
  return {
    ok: false,
    status,
    json: () => (body ? Promise.resolve(body) : Promise.reject(new Error('not json'))),
  };
}

const projectReq = { name: 'P' } as unknown as ProjectRequest;
const credReq = { username: 'u' } as unknown as ProjectGitCredentialRequest;

describe('projectsApi', () => {
  beforeEach(() => {
    mockFetch.mockReset();
    vi.stubGlobal('fetch', mockFetch);
    setAuthToken('tok');
  });
  afterEach(() => vi.unstubAllGlobals());

  describe('requests', () => {
    const cases: Array<{
      name: string;
      call: () => Promise<unknown>;
      url: string;
      method?: string;
      body?: unknown;
    }> = [
      { name: 'loadProjects', call: () => loadProjects(), url: '/api/projects' },
      { name: 'loadAllProjects', call: () => loadAllProjects(), url: '/api/projects/all' },
      {
        name: 'createProject',
        call: () => createProject(projectReq),
        url: '/api/projects',
        method: 'POST',
        body: projectReq,
      },
      {
        name: 'updateProject',
        call: () => updateProject('p1', projectReq),
        url: '/api/projects/p1',
        method: 'PUT',
        body: projectReq,
      },
      {
        name: 'activateProject',
        call: () => activateProject('p1'),
        url: '/api/projects/p1/activate',
        method: 'PATCH',
      },
      {
        name: 'deactivateProject',
        call: () => deactivateProject('p1'),
        url: '/api/projects/p1/deactivate',
        method: 'PATCH',
      },
      {
        name: 'patchNewProject',
        call: () => patchNewProject('p1', 1),
        url: '/api/projects/p1/new-project',
        method: 'PATCH',
        body: { newProject: 1 },
      },
      {
        name: 'getGitCredentials',
        call: () => getGitCredentials('p1'),
        url: '/api/projects/p1/git-credentials',
      },
      {
        name: 'saveGitCredentials',
        call: () => saveGitCredentials('p1', credReq),
        url: '/api/projects/p1/git-credentials',
        method: 'POST',
        body: credReq,
      },
      {
        name: 'deleteGitCredentials',
        call: () => deleteGitCredentials('p1'),
        url: '/api/projects/p1/git-credentials',
        method: 'DELETE',
      },
      {
        name: 'triggerGitCheck',
        call: () => triggerGitCheck('p1'),
        url: '/api/projects/p1/git-check',
        method: 'POST',
      },
      { name: 'getGitStatus', call: () => getGitStatus('p1'), url: '/api/projects/p1/git-status' },
    ];

    it.each(cases)('$name sends an authenticated request', async (c) => {
      mockFetch.mockResolvedValueOnce(ok({ id: 'x' }));
      await c.call();
      const [url, init] = mockFetch.mock.calls[0]!;
      expect(url).toBe(c.url);
      expect(init.method).toBe(c.method);
      expect(init.headers.Authorization).toBe('Bearer tok');
      if (c.body === undefined) {
        expect(init.body).toBeUndefined();
      } else {
        expect(init.headers['Content-Type']).toBe('application/json');
        expect(JSON.parse(init.body)).toEqual(c.body);
      }
    });

    it.each(cases.filter((c) => c.name !== 'saveGitCredentials'))(
      '$name rejects with the status on a failed response',
      async (c) => {
        mockFetch.mockResolvedValueOnce(fail(500));
        await expect(c.call()).rejects.toThrow('500');
      },
    );

    it('returns the parsed body', async () => {
      mockFetch.mockResolvedValueOnce(ok({ id: 'p1' }));
      await expect(createProject(projectReq)).resolves.toEqual({ id: 'p1' });
    });
  });

  it('saveGitCredentials throws an ApiError carrying the server message', async () => {
    mockFetch.mockResolvedValueOnce(fail(422, { message: 'bad token' }));
    const error = await saveGitCredentials('p1', credReq).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ message: 'bad token', status: 422 });
  });

  it('saveGitCredentials falls back to a generic message without a JSON body', async () => {
    mockFetch.mockResolvedValueOnce(fail(401));
    await expect(saveGitCredentials('p1', credReq)).rejects.toThrow(
      'Failed to save git credentials: 401',
    );
  });
});
