import type { InputHTMLAttributes, ReactNode } from 'react';
import styles from '../FormFields.module.scss';
import { fieldHintClass } from '../../Form/Form';

interface CheckboxFieldProps extends Omit<
  InputHTMLAttributes<HTMLInputElement>,
  'type' | 'checked' | 'onChange'
> {
  label: ReactNode;
  checked: boolean;
  onChange: (checked: boolean) => void;
  /** Helper text rendered under the label. */
  hint?: ReactNode;
}

/** A checkbox with its label text beside it, on its own row of the settings grid. */
export function CheckboxField({
  label,
  checked,
  onChange,
  hint,
  ...inputProps
}: CheckboxFieldProps) {
  return (
    <label className={styles.checkboxField}>
      <input
        {...inputProps}
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
      />
      <span>{label}</span>
      {hint && <span className={fieldHintClass}>{hint}</span>}
    </label>
  );
}
