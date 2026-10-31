import { afterEach, describe, expect, it, vi } from 'vitest';
import { describeSourceOutcome, downloadWorkflowBundle, readFileText, toList } from './utils';
import type { SkillCatalogSourceStatus } from '@openjcockpit/api';
import { fixedT } from '@openjcockpit/i18n/testing';
import { workflowResources } from '../i18n';

describe('toList', () => {
  it('splits on commas and trims whitespace', () => {
    expect(toList(' a , b,c ')).toEqual(['a', 'b', 'c']);
  });

  it('drops empty entries', () => {
    expect(toList('a,, ,b,')).toEqual(['a', 'b']);
  });

  it('returns an empty list for blank input', () => {
    expect(toList('')).toEqual([]);
    expect(toList('  ')).toEqual([]);
  });
});

describe('readFileText', () => {
  it('resolves with the file contents', async () => {
    const file = new File(['{"a":1}'], 'in.json', { type: 'application/json' });
    await expect(readFileText(file)).resolves.toBe('{"a":1}');
  });

  it('rejects when the reader fails', async () => {
    const error = new DOMException('boom');
    vi.spyOn(FileReader.prototype, 'readAsText').mockImplementation(function (this: FileReader) {
      Object.defineProperty(this, 'error', { value: error });
      this.onerror?.(new ProgressEvent('error') as ProgressEvent<FileReader>);
    });
    await expect(readFileText(new File(['x'], 'x.json'))).rejects.toBe(error);
    vi.restoreAllMocks();
  });
});

describe('downloadWorkflowBundle', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('downloads the bundle as workflow-export.json and revokes the object URL', async () => {
    const createObjectURL = vi.fn(() => 'blob:test');
    const revokeObjectURL = vi.fn();
    Object.assign(URL, { createObjectURL, revokeObjectURL });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    downloadWorkflowBundle({ groups: [], workflows: [] });

    const clicked = click.mock.contexts[0] as HTMLAnchorElement;
    expect(clicked.download).toBe('workflow-export.json');
    expect(clicked.href).toBe('blob:test');
    const blob = (createObjectURL.mock.calls[0] as unknown as [Blob])[0];
    expect(blob.type).toBe('application/json');
    expect(await readFileText(blob as File)).toBe(
      JSON.stringify({ groups: [], workflows: [] }, null, 2),
    );
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:test');
  });
});

describe('describeSourceOutcome', () => {
  const t = fixedT('workflow', workflowResources);
  const source = (
    outcome: SkillCatalogSourceStatus['outcome'],
    httpStatus: number | null = null,
  ): SkillCatalogSourceStatus => ({
    kind: 'MARKETPLACE',
    marketplaceId: 'm1',
    name: 'Hub',
    outcome,
    itemCount: 0,
    httpStatus,
  });

  it.each([
    ['AUTH_FAILED', 'Hub: authentication was rejected (HTTP 401).', 401],
    ['TIMEOUT', 'Hub: timed out.', null],
    ['UNREACHABLE', 'Hub: could not be reached.', null],
    ['INVALID_RESPONSE', 'Hub: returned an invalid response.', null],
    ['BLOCKED_BY_POLICY', 'Hub: blocked by egress policy.', null],
    ['CONFIG_ERROR', 'Hub: configuration error — check its API key.', null],
    ['SUCCESS', 'Hub: unknown status.', null],
  ] as const)('describes %s', (outcome, expected, status) => {
    expect(describeSourceOutcome(source(outcome, status), t)).toBe(expected);
  });

  it('falls back to "unknown" when the auth failure has no HTTP status', () => {
    expect(describeSourceOutcome(source('AUTH_FAILED'), t)).toBe(
      'Hub: authentication was rejected (HTTP unknown).',
    );
  });
});
