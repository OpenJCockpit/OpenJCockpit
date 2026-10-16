import { forwardRef } from 'react';
import type { ButtonHTMLAttributes } from 'react';
import { cx } from '../cx';
import styles from './Button.module.scss';

export type ButtonVariant = 'default' | 'primary' | 'secondary' | 'danger' | 'workflow' | 'link';
export type ButtonSize = 'md' | 'sm';

interface ButtonStyle {
  variant?: ButtonVariant;
  /** `sm` is the compact padding used for secondary toolbar actions. */
  size?: ButtonSize;
}

const variantClass: Record<Exclude<ButtonVariant, 'default'>, string> = {
  primary: styles.primary,
  secondary: styles.secondary,
  danger: styles.danger,
  workflow: styles.workflow,
  link: styles.link,
};

/** Class names for a button look; use it directly on elements that are not a <button>, e.g. an <a>. */
export function buttonClass({ variant = 'default', size = 'md' }: ButtonStyle = {}): string {
  // `secondary` is a standalone look; every other variant builds on the shared base.
  if (variant === 'secondary') return styles.secondary;
  return cx(
    styles.base,
    size === 'sm' && styles.small,
    variant !== 'default' && variantClass[variant],
  );
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement>, ButtonStyle {}

/** The dashboard's button. Does not set `type`, so forms behave as with a plain <button>. */
export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant, size, className, ...rest },
  ref,
) {
  return <button ref={ref} className={cx(buttonClass({ variant, size }), className)} {...rest} />;
});
