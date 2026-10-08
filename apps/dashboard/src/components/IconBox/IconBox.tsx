export function IconBox({
  label,
  variant,
  compact,
}: {
  label: string;
  variant?: 'hero';
  compact?: boolean;
}) {
  return (
    <span
      className={`icon-box ${variant ? `icon-box--${variant}` : ''} ${compact ? 'icon-box--compact' : ''}`}
      aria-hidden="true"
    >
      {label}
    </span>
  );
}
