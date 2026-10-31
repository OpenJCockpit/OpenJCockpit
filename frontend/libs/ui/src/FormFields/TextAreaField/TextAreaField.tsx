import type { ReactNode, TextareaHTMLAttributes } from 'react';
import styles from '../FormFields.module.scss';
import { formInputClass } from '../../Form/Form';
import { cx } from '../../cx';
import { FieldLabelText, FieldMessages, useFieldA11y } from '../FieldParts';

type TextAreaProps = Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'onChange' | 'value'>;

interface TextAreaFieldProps extends TextAreaProps {
  label: ReactNode;
  value: string;
  onChange: (value: string) => void;
  wide?: boolean;
  hint?: ReactNode;
  /** Inline validation message under the field; marks it invalid. */
  error?: ReactNode;
  errorId?: string;
}

export function TextAreaField({
  label,
  value,
  onChange,
  wide,
  hint,
  error,
  errorId,
  required,
  className,
  ...textAreaProps
}: TextAreaFieldProps) {
  const a11y = useFieldA11y({
    error,
    errorId,
    hint,
    required,
    'aria-describedby': textAreaProps['aria-describedby'],
    'aria-invalid': textAreaProps['aria-invalid'],
  });
  return (
    <label className={wide ? styles.formFieldWide : undefined}>
      <FieldLabelText label={label} required={required} />
      <textarea
        {...textAreaProps}
        {...a11y.inputProps}
        className={cx(formInputClass, className)}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
      <FieldMessages hint={hint} hintId={a11y.hintId} error={error} errorId={a11y.errorId} />
    </label>
  );
}
