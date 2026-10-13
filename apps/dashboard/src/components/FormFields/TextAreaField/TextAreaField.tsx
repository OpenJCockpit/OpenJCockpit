import type { ReactNode, TextareaHTMLAttributes } from 'react';
import '../FormFields.scss';

type TextAreaProps = Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'onChange' | 'value'>;

interface TextAreaFieldProps extends TextAreaProps {
  label: ReactNode;
  value: string;
  onChange: (value: string) => void;
  wide?: boolean;
  hint?: ReactNode;
}

export function TextAreaField({
  label,
  value,
  onChange,
  wide,
  hint,
  className,
  ...textAreaProps
}: TextAreaFieldProps) {
  return (
    <label className={wide ? 'form-field--wide' : undefined}>
      {label}
      <textarea
        {...textAreaProps}
        className={['settings-input', className].filter(Boolean).join(' ')}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
      {hint && <span className="settings-desc">{hint}</span>}
    </label>
  );
}
