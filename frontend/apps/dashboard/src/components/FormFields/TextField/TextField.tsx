import { forwardRef } from 'react';
import type { InputHTMLAttributes, ReactNode } from 'react';
import styles from '../FormFields.module.scss';
import { fieldHintClass, formInputClass, cx } from '@openjcockpit/ui';

type TextInputProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'onChange' | 'value'>;

interface TextFieldProps extends TextInputProps {
  label: ReactNode;
  value: string;
  onChange: (value: string) => void;
  /** Span the full width of the settings grid. */
  wide?: boolean;
  /** Helper text rendered under the input. */
  hint?: ReactNode;
}

export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(function TextField(
  { label, value, onChange, wide, hint, className, ...inputProps },
  ref,
) {
  return (
    <label className={wide ? styles.formFieldWide : undefined}>
      {label}
      <input
        ref={ref}
        {...inputProps}
        className={cx(formInputClass, className)}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
      {hint && <span className={fieldHintClass}>{hint}</span>}
    </label>
  );
});
