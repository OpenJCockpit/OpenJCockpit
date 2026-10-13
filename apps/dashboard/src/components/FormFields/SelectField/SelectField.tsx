import type { ReactNode, SelectHTMLAttributes } from 'react';
import '../FormFields.scss';

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
    <label className={wide ? 'form-field--wide' : undefined}>
      {label}
      <select
        {...selectProps}
        className={['settings-input', className].filter(Boolean).join(' ')}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      >
        {children}
      </select>
      {hint && <span className="settings-desc">{hint}</span>}
    </label>
  );
}
