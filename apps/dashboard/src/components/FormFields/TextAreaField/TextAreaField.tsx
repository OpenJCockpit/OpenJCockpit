import type { ReactNode, TextareaHTMLAttributes } from 'react';
import '../FormFields.scss';
import { fieldHintClass, formInputClass } from '../../../ui/Form/Form';
import { cx } from '../../../ui/cx';

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
        className={cx(formInputClass, className)}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
      {hint && <span className={fieldHintClass}>{hint}</span>}
    </label>
  );
}
