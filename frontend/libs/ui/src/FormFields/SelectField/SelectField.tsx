import type { ReactNode } from 'react';
import styles from '../FormFields.module.scss';
import { fieldHintClass } from '../../Form/Form';
import { Select } from '../../Select/Select';
import type { SelectOption } from '../../Select/Select';

interface SelectFieldProps {
  label: ReactNode;
  value: string;
  options: SelectOption[];
  onChange: (value: string) => void;
  wide?: boolean;
  hint?: ReactNode;
  id?: string;
  disabled?: boolean;
  className?: string;
  'aria-describedby'?: string;
  'data-testid'?: string;
}

export function SelectField({
  label,
  value,
  options,
  onChange,
  wide,
  hint,
  ...selectProps
}: SelectFieldProps) {
  return (
    <label className={wide ? styles.formFieldWide : undefined}>
      {label}
      <Select {...selectProps} block value={value} options={options} onChange={onChange} />
      {hint && <span className={fieldHintClass}>{hint}</span>}
    </label>
  );
}
