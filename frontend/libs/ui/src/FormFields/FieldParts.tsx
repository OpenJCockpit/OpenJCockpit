import { useId } from 'react';
import type { ReactNode } from 'react';
import { fieldErrorClass, fieldHintClass } from '../Form/Form';
import { cx } from '../cx';
import styles from './FormFields.module.scss';

/** Label text plus the one required marker every form uses. The asterisk is decoration; `aria-required` carries the meaning. */
export function FieldLabelText({ label, required }: { label: ReactNode; required?: boolean }) {
  return (
    <>
      {label}
      {required && (
        <span className={styles.requiredMark} aria-hidden="true">
          {' *'}
        </span>
      )}
    </>
  );
}

interface FieldA11yInput {
  error?: ReactNode;
  errorId?: string;
  hint?: ReactNode;
  required?: boolean;
  'aria-describedby'?: string;
  'aria-invalid'?: boolean | 'true' | 'false' | 'grammar' | 'spelling';
}

/** Ids and ARIA attributes tying an input to its inline error and hint. */
export function useFieldA11y({ error, errorId, hint, required, ...aria }: FieldA11yInput) {
  const generated = useId();
  const resolvedErrorId = errorId ?? `${generated}-error`;
  const hintId = `${generated}-hint`;
  const describedBy =
    [aria['aria-describedby'], error ? resolvedErrorId : undefined, hint ? hintId : undefined]
      .filter(Boolean)
      .join(' ') || undefined;
  return {
    errorId: resolvedErrorId,
    hintId,
    inputProps: {
      'aria-describedby': describedBy,
      'aria-invalid': error ? true : aria['aria-invalid'],
      'aria-required': required ? true : undefined,
    },
  };
}

/** Hint under a field, and the inline error that replaces the far-away page banner. */
export function FieldMessages({
  hint,
  hintId,
  error,
  errorId,
}: {
  hint?: ReactNode;
  hintId: string;
  error?: ReactNode;
  errorId: string;
}) {
  return (
    <>
      {hint && (
        <span id={hintId} className={fieldHintClass}>
          {hint}
        </span>
      )}
      {error && (
        <span id={errorId} role="alert" className={cx(fieldErrorClass, styles.inlineError)}>
          {error}
        </span>
      )}
    </>
  );
}
