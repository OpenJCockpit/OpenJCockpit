import { renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useProject } from './ProjectContext';

describe('useProject', () => {
  it('throws outside RequireProject', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);

    expect(() => renderHook(() => useProject())).toThrow(
      'useProject must be used within RequireProject',
    );
  });
});
