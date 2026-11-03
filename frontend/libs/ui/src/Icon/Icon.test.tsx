import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Icon, isIconName } from './Icon';
import type { IconName } from './Icon';

const NAMES: IconName[] = [
  'check',
  'close',
  'refresh',
  'settings',
  'search',
  'plus',
  'arrow-left',
  'arrow-right',
  'external',
  'spinner',
  'github',
  'platform',
];

describe('Icon', () => {
  it.each(NAMES)('draws %s as a decorative svg path', (name) => {
    const { container } = render(<Icon name={name} />);
    const svg = container.querySelector('svg');
    expect(svg).toHaveAttribute('aria-hidden', 'true');
    expect(container.querySelector('path')?.getAttribute('d')).toBeTruthy();
  });

  it('forwards a className', () => {
    const { container } = render(<Icon name="check" className="extra" />);
    expect(container.querySelector('svg')).toHaveClass('extra');
  });

  it('recognises drawable icon names only', () => {
    expect(isIconName('check')).toBe(true);
    expect(isIconName('not-an-icon')).toBe(false);
    expect(isIconName('toString')).toBe(false);
  });
});
