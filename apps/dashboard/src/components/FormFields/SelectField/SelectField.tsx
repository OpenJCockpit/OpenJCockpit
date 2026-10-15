import type { ReactNode, SelectHTMLAttributes } from 'react';
import styles from '../FormFields.module.scss';
import { fieldHintClass, formInputClass } from '../../../ui/Form/Form';
import { cx } from '../../../ui/cx';

type SelectProps = Omit<SelectHTMLAttributes<HTMLSelectElement>, 'onChange' | 'value'>;

interface SelectFieldProps extends SelectProps {
  label: ReactNode;
  value: string;
  onChange: (value: string) => void;
  wide?: boolean;
  hint?: ReactNode;
  children: ReactNode;
}

export function SelectField({
  label,
  value,
  onChange,
  wide,
  hint,
  className,
  children,
  ...selectProps
}: SelectFieldProps) {
  return (
    <label className={wide ? styles.formFieldWide : undefined}>
      {label}
      <select
        {...selectProps}
        className={cx(formInputClass, className)}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      >
        {children}
      </select>
      {hint && <span className={fieldHintClass}>{hint}</span>}
    </label>
  );
}
