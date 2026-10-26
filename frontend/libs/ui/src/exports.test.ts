import { describe, expect, it } from 'vitest';
import * as ui from './index';
import { feedbackErrorClass, feedbackResultClass } from './Feedback/Feedback';
import { pageClass, pageHeaderClass } from './Page/Page';
import { dropdownClass } from './Dropdown/Dropdown';

describe('public API', () => {
  it('exposes the shared components and helpers', () => {
    for (const name of ['Button', 'Panel', 'DataTable', 'AppShell', 'FrameOctagon', 'cx']) {
      expect(ui).toHaveProperty(name);
    }
  });

  it('exposes non-empty shared class names', () => {
    for (const cls of [
      feedbackErrorClass,
      feedbackResultClass,
      pageClass,
      pageHeaderClass,
      dropdownClass,
    ]) {
      expect(typeof cls).toBe('string');
      expect(cls).not.toBe('');
    }
  });
});
