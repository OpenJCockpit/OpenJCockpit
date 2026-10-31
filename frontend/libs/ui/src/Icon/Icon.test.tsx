import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Icon } from './Icon';
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
});
