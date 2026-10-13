import type { SpecFile } from '../../types';
import { IconBox } from '../IconBox/IconBox';

interface Props {
  spec: SpecFile;
  selected: boolean;
  onSelect: (id: string) => void;
}

/** A selectable card for one spec file in the spec list. */
export function SpecCardButton({ spec, selected, onSelect }: Props) {
  return (
    <button
      className={`spec-card ${selected ? 'is-selected' : ''}`}
      onClick={() => onSelect(spec.id)}
      aria-pressed={selected}
    >
      <IconBox label="📄" compact />
      <div>
        <strong>{spec.fileName}</strong>
        <span>Last modified: {spec.lastChanged}</span>
      </div>
      {selected && <span className="active-rail" aria-hidden="true" />}
    </button>
  );
}
