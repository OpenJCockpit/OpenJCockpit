import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  enqueueSpecQueueItem,
  getSpecQueue,
  getSpecQueueSettings,
  listSpecFilesStrict,
  pauseSpecQueue,
  removeSpecQueueItem,
  reorderSpecQueue,
  resumeSpecQueue,
  retrySpecQueueItem,
  skipSpecQueueItem,
  updateSpecQueueItem,
  updateSpecQueueSettings,
} from './specQueueApi';

const mockFetch = vi.fn();

function ok(body: unknown) {
  return { ok: true, status: 200, json: () => Promise.resolve(body) };
}
function fail(status: number, body?: unknown) {
  return {
    ok: false,
    status,
    json: () => (body ? Promise.resolve(body) : Promise.reject(new Error('not json'))),
  };
}

describe('specQueueApi', () => {
  beforeEach(() => {
    mockFetch.mockReset();
    vi.stubGlobal('fetch', mockFetch);
  });
  afterEach(() => vi.unstubAllGlobals());

  it('GETs the queue without a body or content type', async () => {
    mockFetch.mockResolvedValueOnce(ok({ items: [] }));
    await getSpecQueue('p 1');
    const [url, init] = mockFetch.mock.calls[0]!;
    expect(url).toContain('/api/projects/p%201/spec-queue');
    expect(init.method).toBe('GET');
    expect(init.body).toBeUndefined();
    expect(init.headers['Content-Type']).toBeUndefined();
  });

  it('POSTs JSON when enqueueing', async () => {
    mockFetch.mockResolvedValueOnce(ok({ id: 'i1' }));
    await enqueueSpecQueueItem('p1', {
      specFile: 'a.md',
      workflowId: 'w',
      autoMerge: false,
    });
    const [url, init] = mockFetch.mock.calls[0]!;
    expect(url).toContain('/spec-queue/items');
    expect(init.method).toBe('POST');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(init.body)).toEqual({
      specFile: 'a.md',
      workflowId: 'w',
      autoMerge: false,
    });
  });

  it('PUTs the complete id list when reordering', async () => {
    mockFetch.mockResolvedValueOnce(ok({ items: [] }));
    await reorderSpecQueue('p1', ['a', 'b']);
    const [url, init] = mockFetch.mock.calls[0]!;
    expect(url).toContain('/spec-queue/order');
    expect(init.method).toBe('PUT');
    expect(JSON.parse(init.body)).toEqual({ itemIds: ['a', 'b'] });
  });

  it('DELETEs an item with an encoded id', async () => {
    mockFetch.mockResolvedValueOnce(ok({ id: 'x/y' }));
    await removeSpecQueueItem('p1', 'x/y');
    expect(mockFetch.mock.calls[0]![0]).toContain('/items/x%2Fy');
    expect(mockFetch.mock.calls[0]![1].method).toBe('DELETE');
  });

  it('throws the server message, or a fallback for non-JSON bodies', async () => {
    mockFetch.mockResolvedValueOnce(fail(409, { code: 'X', message: 'Order is stale' }));
    await expect(reorderSpecQueue('p1', [])).rejects.toThrow('Order is stale');
    mockFetch.mockResolvedValueOnce(fail(500));
    await expect(pauseSpecQueue('p1')).rejects.toThrow('Failed to pause the queue: 500');
  });

  it('maps a 403 on the settings PUT to a fixed message', async () => {
    mockFetch.mockResolvedValueOnce(fail(403));
    await expect(updateSpecQueueSettings('p1', true)).rejects.toThrow(
      'Only administrators can change this setting.',
    );
  });

  it("prefers the server's message on a 403 settings PUT", async () => {
    mockFetch.mockResolvedValueOnce(fail(403, { message: 'Project is archived' }));
    await expect(updateSpecQueueSettings('p1', true)).rejects.toThrow('Project is archived');
  });

  it('uses the queue-settings fallback for other settings failures', async () => {
    mockFetch.mockResolvedValueOnce(fail(500));
    await expect(updateSpecQueueSettings('p1', true)).rejects.toThrow(
      'Failed to update the queue settings: 500',
    );
  });

  it('listSpecFilesStrict throws the server message on a 502', async () => {
    mockFetch.mockResolvedValueOnce(
      fail(502, { code: 'SPEC_LISTING_FAILED', message: 'git down' }),
    );
    await expect(listSpecFilesStrict('p1')).rejects.toThrow('git down');
  });

  it('PATCHes JSON when updating an item', async () => {
    mockFetch.mockResolvedValueOnce(ok({ id: 'i1' }));
    await updateSpecQueueItem('p1', 'i1', { autoMerge: true });
    const [url, init] = mockFetch.mock.calls[0]!;
    expect(url).toContain('/spec-queue/items/i1');
    expect(init.method).toBe('PATCH');
    expect(JSON.parse(init.body)).toEqual({ autoMerge: true });
  });

  it.each([
    ['resume', () => resumeSpecQueue('p1'), '/spec-queue/resume'],
    ['retry', () => retrySpecQueueItem('p1', 'i1'), '/items/i1/retry'],
    ['skip', () => skipSpecQueueItem('p1', 'i1'), '/items/i1/skip'],
  ])('POSTs to %s without a body', async (_name, call, suffix) => {
    mockFetch.mockResolvedValueOnce(ok({}));
    await call();
    const [url, init] = mockFetch.mock.calls[0]!;
    expect(url).toContain(suffix);
    expect(init.method).toBe('POST');
    expect(init.body).toBeUndefined();
  });

  it('GETs the queue settings', async () => {
    mockFetch.mockResolvedValueOnce(ok({ autoMergeAllowed: true }));
    await expect(getSpecQueueSettings('p1')).resolves.toEqual({ autoMergeAllowed: true });
    expect(mockFetch.mock.calls[0]![0]).toContain('/spec-queue/settings');
  });

  it('PUTs the settings and returns the response', async () => {
    mockFetch.mockResolvedValueOnce(ok({ autoMergeAllowed: false }));
    await expect(updateSpecQueueSettings('p1', false)).resolves.toEqual({
      autoMergeAllowed: false,
    });
    const [, init] = mockFetch.mock.calls[0]!;
    expect(init.method).toBe('PUT');
    expect(JSON.parse(init.body)).toEqual({ autoMergeAllowed: false });
  });

  it('listSpecFilesStrict returns the listing on success', async () => {
    mockFetch.mockResolvedValueOnce(ok([{ path: 'a.md' }]));
    await expect(listSpecFilesStrict('p 1')).resolves.toEqual([{ path: 'a.md' }]);
    expect(mockFetch.mock.calls[0]![0]).toContain('/api/projects/p%201/spec-files');
  });
});
