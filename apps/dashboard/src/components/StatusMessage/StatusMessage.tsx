import type { HTMLAttributes } from 'react';

interface StatusMessageProps extends HTMLAttributes<HTMLParagraphElement> {
  /** `error` renders the red settings-error line; `info` the muted settings-desc line. */
  variant?: 'error' | 'info';
}

/** Inline feedback paragraph shared by the settings and workflow panels. */
export function StatusMessage({
  variant = 'info',
  className,
  children,
  ...rest
}: StatusMessageProps) {
  const base = variant === 'error' ? 'settings-error' : 'settings-desc';
  return (
    <p className={className ? `${base} ${className}` : base} {...rest}>
      {children}
    </p>
  );
}
