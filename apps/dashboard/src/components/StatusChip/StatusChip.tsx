import './StatusChip.scss';
import type { StatusKind } from '../../types';

export function StatusChip({ label, kind }: { label: string; kind: StatusKind }) {
  return <span className={`status-chip status-chip--${kind}`}>{label}</span>;
}
