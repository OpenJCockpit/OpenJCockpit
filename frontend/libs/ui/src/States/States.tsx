import type { ReactNode } from 'react';
import { Button } from '../Button/Button';
import { fieldErrorClass } from '../Form/Form';
import { cx } from '../cx';

import styles from './States.module.scss';

/** The single ellipsis character for loading copy; use it in the translated strings too. */
export const ELLIPSIS = '…';

interface LoadingStateProps {
  /** Specific copy such as "Loading the queue…"; the caller translates it. */
  children: ReactNode;
  className?: string;
}

/** Inline "still loading" line for panels and widgets. Full-page loading stays `StatusMessage variant="loading"`. */
export function LoadingState({ children, className }: LoadingStateProps) {
  return (
    <p className={cx(styles.loadingState, className)} role="status" aria-live="polite">
      {children}
    </p>
  );
}

interface EmptyStateProps {
  title: ReactNode;
  hint?: ReactNode;
  /** The next step, usually a button or link. */
  action?: ReactNode;
  className?: string;
}

/** Nothing here yet: says so, explains why, and offers the next step if there is one. */
export function EmptyState({ title, hint, action, className }: EmptyStateProps) {
  return (
    <div className={cx(styles.emptyState, className)}>
      <p className={styles.emptyTitle}>{title}</p>
      {hint && <p className={styles.emptyHint}>{hint}</p>}
      {action}
    </div>
  );
}

interface ErrorStateProps {
  children: ReactNode;
  onRetry?: () => void;
  /** Translated label of the retry button; required together with `onRetry`. */
  retryLabel?: string;
  className?: string;
}

/** A failed load or save: announced as an alert, with Retry when the caller can retry. */
export function ErrorState({ children, onRetry, retryLabel, className }: ErrorStateProps) {
  return (
    <div className={cx(styles.errorState, className)}>
      <p className={fieldErrorClass} role="alert">
        {children}
      </p>
      {onRetry && retryLabel && (
        <Button variant="secondary" onClick={onRetry}>
          {retryLabel}
        </Button>
      )}
    </div>
  );
}
