import type { ButtonHTMLAttributes } from 'react';

interface ToolbarButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** `primary` is the prominent start button; `small` the compact secondary one. */
  variant?: 'primary' | 'small';
}

/** Button with the shared `button` styling variants. */
export function ToolbarButton({
  variant = 'primary',
  className,
  children,
  ...rest
}: ToolbarButtonProps) {
  const base = `button button--${variant === 'primary' ? 'start' : 'small'}`;
  return (
    <button className={className ? `${base} ${className}` : base} {...rest}>
      {children}
    </button>
  );
}
