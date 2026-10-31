import { describe, expect, it } from 'vitest';
import { hostApp } from './hostApp';

describe('hostApp', () => {
  it('contributes only settings sections, none of them in the nav', () => {
    expect(hostApp.showInNav).toBe(false);
    expect(hostApp.settings?.map((section) => section.id)).toEqual(['preferences', 'projects']);
  });

  it('loads the routes as empty and each section as a component', async () => {
    await expect(hostApp.loadRoutes()).resolves.toEqual([]);
    for (const section of hostApp.settings ?? []) {
      const loaded = await section.component();
      expect(loaded.default).toBeTruthy();
    }
  });
});
