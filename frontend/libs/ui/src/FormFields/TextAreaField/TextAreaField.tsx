import type { ReactNode, TextareaHTMLAttributes } from 'react';
import styles from '../FormFields.module.scss';
import { fieldHintClass, formInputClass } from '../../Form/Form';
import { cx } from '../../cx';

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
    <label className={wide ? styles.formFieldWide : undefined}>
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
