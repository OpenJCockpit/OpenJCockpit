import { render, screen } from '@testing-library/react';
import { createRef } from 'react';
import { describe, expect, it } from 'vitest';
import { Button, buttonClass } from './Button';

const VARIANTS = ['default', 'primary', 'secondary', 'danger', 'workflow', 'link'] as const;

describe('Button', () => {
  it('gives every variant a distinct, non-empty class', () => {
    const classes = VARIANTS.map((variant) => buttonClass({ variant }));
    classes.forEach((c) => expect(c).toBeTruthy());
    expect(new Set(classes).size).toBe(VARIANTS.length);
  });

  it('adds the small size on top of the variant', () => {
    expect(buttonClass({ variant: 'primary', size: 'sm' })).not.toBe(
      buttonClass({ variant: 'primary' }),
    );
  });

  it('merges className, forwards the ref and does not default the type', () => {
    const ref = createRef<HTMLButtonElement>();
    render(
      <Button ref={ref} className="extra" variant="danger">
        Remove
      </Button>,
    );
    const button = screen.getByRole('button', { name: 'Remove' });
    expect(button).toHaveClass('extra');
    expect(button.className).toContain(buttonClass({ variant: 'danger' }));
    expect(ref.current).toBe(button);
    expect(button).not.toHaveAttribute('type');
  });
});
