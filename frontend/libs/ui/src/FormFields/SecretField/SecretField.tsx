import type { ReactNode } from 'react';
import { useTranslation } from '@openjcockpit/i18n';
import { fieldHintClass, formInputClass } from '../../Form/Form';
import FormFieldsStyles from '../FormFields.module.scss';
import { FieldLabelText } from '../FieldParts';

interface SecretFieldProps {
  label: ReactNode;
  /** Whether a secret is already stored server-side; an empty field then keeps it. */
  hasValue: boolean;
  value: string;
  onChange: (value: string) => void;
  required?: boolean;
}

/** Password input for a write-only secret: blank means "keep the stored value". */
export function SecretField({ label, hasValue, value, onChange, required }: SecretFieldProps) {
  const { t } = useTranslation();
  return (
    <label className={FormFieldsStyles.formFieldWide}>
      <FieldLabelText label={label} required={required} />{' '}
      {hasValue && <span className={fieldHintClass}>{t('secretField.configuredHint')}</span>}
      <input
        className={formInputClass}
        type="password"
        aria-required={required ? true : undefined}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={hasValue ? '••••••••' : ''}
      />
    </label>
  );
}
