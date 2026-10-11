import { forwardRef } from 'react';
import type { InputHTMLAttributes, ReactNode, TextareaHTMLAttributes } from 'react';

const WIDE = { gridColumn: '1 / -1' } as const;

/** Base input class plus any caller-supplied classes. */
const joinClasses = (base: string, extra?: string) => (extra ? `${base} ${extra}` : base);

type TextInputProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'onChange' | 'value'>;

interface TextFieldProps extends TextInputProps {
  label: ReactNode;
  value: string;
  onChange: (value: string) => void;
  /** Span the full width of the settings grid. */
  wide?: boolean;
}

export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(function TextField(
  { label, value, onChange, wide, className, ...inputProps },
  ref,
) {
  return (
    <label style={wide ? WIDE : undefined}>
      {label}
      <input
        ref={ref}
        {...inputProps}
        className={joinClasses('settings-input', className)}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
    </label>
  );
});

type TextAreaProps = Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'onChange' | 'value'>;

interface TextAreaFieldProps extends TextAreaProps {
  label: ReactNode;
  value: string;
  onChange: (value: string) => void;
  wide?: boolean;
}

export function TextAreaField({
  label,
  value,
  onChange,
  wide,
  className,
  ...textAreaProps
}: TextAreaFieldProps) {
  return (
    <label style={wide ? WIDE : undefined}>
      {label}
      <textarea
        {...textAreaProps}
        className={joinClasses('settings-input', className)}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
    </label>
  );
}

interface CheckboxFieldProps {
  label: ReactNode;
  checked: boolean;
  onChange: (checked: boolean) => void;
}

/** A checkbox whose label text precedes the box. */
export function CheckboxField({ label, checked, onChange }: CheckboxFieldProps) {
  return (
    <label>
      {label}
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} />
    </label>
  );
}

interface SecretFieldProps {
  label: ReactNode;
  /** Whether a secret is already stored server-side; an empty field then keeps it. */
  hasValue: boolean;
  value: string;
  onChange: (value: string) => void;
}

/** Password input for a write-only secret: blank means "keep the stored value". */
export function SecretField({ label, hasValue, value, onChange }: SecretFieldProps) {
  return (
    <label style={WIDE}>
      {label}{' '}
      {hasValue && (
        <span className="settings-desc">(already configured — leave blank to keep)</span>
      )}
      <input
        className="settings-input"
        type="password"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={hasValue ? '••••••••' : ''}
      />
    </label>
  );
}

interface IntegrationConfigFormProps {
  title: string;
  saveLabel: string;
  saving: boolean;
  onSave: () => void;
  children: ReactNode;
}

/** A titled settings form with a grid of fields and a single save button. */
export function IntegrationConfigForm({
  title,
  saveLabel,
  saving,
  onSave,
  children,
}: IntegrationConfigFormProps) {
  return (
    <div className="settings-form">
      <h2 className="settings-form-title">{title}</h2>
      <div className="settings-form-grid">{children}</div>
      <button className="button button--start" onClick={onSave} disabled={saving}>
        {saving ? '⟳ Saving…' : saveLabel}
      </button>
    </div>
  );
}
