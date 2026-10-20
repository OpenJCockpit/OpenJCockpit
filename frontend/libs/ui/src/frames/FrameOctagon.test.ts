import { describe, expect, it } from 'vitest';
import { buildFrameOctagonPaths } from './FrameOctagon';

describe('buildFrameOctagonPaths', () => {
  it('returns a closed bg path and two open line paths for a measured size', () => {
    const { bg, lineA, lineB } = buildFrameOctagonPaths(200, 100);

    expect(bg.endsWith('Z')).toBe(true);
    expect(lineA).not.toContain('Z');
    expect(lineB).not.toContain('Z');
  });

  it('treats non-finite and negative sizes as zero', () => {
    expect(buildFrameOctagonPaths(Number.NaN, -5)).toEqual(buildFrameOctagonPaths(0, 0));
  });
});
