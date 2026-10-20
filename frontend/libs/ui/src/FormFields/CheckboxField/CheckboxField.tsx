import type { ReactNode } from 'react';

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
