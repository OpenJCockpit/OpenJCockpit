import '@testing-library/jest-dom/vitest';
import * as axeMatchers from 'vitest-axe/matchers';
import type { AxeMatchers } from 'vitest-axe/matchers';
import { expect } from 'vitest';

expect.extend(axeMatchers);

declare module 'vitest' {
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type -- vitest matcher augmentation merges into the interface
  interface Assertion extends AxeMatchers {}
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type -- vitest matcher augmentation merges into the interface
  interface AsymmetricMatchersContaining extends AxeMatchers {}
}
