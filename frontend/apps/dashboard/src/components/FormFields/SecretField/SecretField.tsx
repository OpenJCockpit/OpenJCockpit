import type { ReactNode } from 'react';
import { fieldHintClass, formInputClass } from '@openjcockpit/ui';
import FormFieldsStyles from '../FormFields.module.scss';

interface SecretFieldProps {
  label: ReactNode;
  /** Whether a secret is already stored server-side; an empty field then keeps it. */
  hasValue: boolean;
  value: string;
  onChange: (value: string) => void;
}

/** Password input for a write-only secret: blank means "keep the stored value". */
export function SecretField({ label, hasValue, value, onChange }: SecretFieldProps) {
  return (
    <label className={FormFieldsStyles.formFieldWide}>
      {label}{' '}
      {hasValue && (
        <span className={fieldHintClass}>(already configured — leave blank to keep)</span>
      )}
      <input
        className={formInputClass}
        type="password"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={hasValue ? '••••••••' : ''}
      />
    </label>
  );
}
