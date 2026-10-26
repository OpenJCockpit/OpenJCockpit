import { beforeEach, describe, expect, it } from 'vitest';
import { clearStoredProjectId, getStoredProjectId, setStoredProjectId } from './storedProject';

describe('storedProject', () => {
  beforeEach(() => localStorage.clear());

  it('returns null when nothing is stored', () => {
    expect(getStoredProjectId()).toBeNull();
  });

  it('round-trips the project id', () => {
    setStoredProjectId('p1');
    expect(getStoredProjectId()).toBe('p1');
  });

  it('clears the stored id', () => {
    setStoredProjectId('p1');
    clearStoredProjectId();
    expect(getStoredProjectId()).toBeNull();
  });
});
