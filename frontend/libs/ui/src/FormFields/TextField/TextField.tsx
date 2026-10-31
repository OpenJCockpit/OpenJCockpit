import { forwardRef } from 'react';
import type { InputHTMLAttributes, ReactNode } from 'react';
import styles from '../FormFields.module.scss';
import { formInputClass } from '../../Form/Form';
import { cx } from '../../cx';
import { FieldLabelText, FieldMessages, useFieldA11y } from '../FieldParts';

type TextInputProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'onChange' | 'value'>;

interface TextFieldProps extends TextInputProps {
  label: ReactNode;
  value: string;
  onChange: (value: string) => void;
  /** Span the full width of the settings grid. */
  wide?: boolean;
  /** Helper text rendered under the input. */
  hint?: ReactNode;
  /** Inline validation message under the input; marks the input invalid. */
  error?: ReactNode;
  /** Id for the error element when something else must reference it. */
  errorId?: string;
}

export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(function TextField(
  { label, value, onChange, wide, hint, error, errorId, required, className, ...inputProps },
  ref,
) {
  const a11y = useFieldA11y({
    error,
    errorId,
    hint,
    required,
    'aria-describedby': inputProps['aria-describedby'],
    'aria-invalid': inputProps['aria-invalid'],
  });
  return (
    <label className={wide ? styles.formFieldWide : undefined}>
      <FieldLabelText label={label} required={required} />
      <input
        ref={ref}
        {...inputProps}
        {...a11y.inputProps}
        className={cx(formInputClass, className)}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
      <FieldMessages hint={hint} hintId={a11y.hintId} error={error} errorId={a11y.errorId} />
    </label>
  );
});
