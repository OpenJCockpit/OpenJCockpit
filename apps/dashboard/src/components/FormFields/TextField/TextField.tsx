import { forwardRef } from 'react';
import type { InputHTMLAttributes, ReactNode } from 'react';
import '../FormFields.scss';

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
    <label className={wide ? 'form-field--wide' : undefined}>
      {label}
      <input
        ref={ref}
        {...inputProps}
        className={['settings-input', className].filter(Boolean).join(' ')}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
      {hint && <span className="settings-desc">{hint}</span>}
    </label>
  );
});
