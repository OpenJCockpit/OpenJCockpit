import type { ButtonHTMLAttributes } from 'react';
import { Button } from '@openjcockpit/ui';

interface ToolbarButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** `primary` is the prominent start button; `small` the compact secondary one. */
  variant?: 'primary' | 'small';
}

/** Toolbar flavour of Button: prominent `primary`, or the compact default-looking `small`. */
export function ToolbarButton({ variant = 'primary', ...rest }: ToolbarButtonProps) {
  return variant === 'primary' ? (
    <Button variant="primary" {...rest} />
  ) : (
    <Button size="sm" {...rest} />
  );
}
