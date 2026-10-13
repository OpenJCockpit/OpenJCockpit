import type { SpecFile } from '../../types';
import { IconBox } from '../IconBox/IconBox';
import { NoSpecsFound } from './NoSpecsFound';
import { SpecCardButton } from './SpecCardButton';

interface Props {
  specs: SpecFile[];
  loading: boolean;
  selectedSpecId: string;
  selected: SpecFile | undefined;
  onSelect: (id: string) => void;
  onGoToWorkflow: () => void;
  onViewAll: () => void;
}

export function SpecPanel({
  specs,
  loading,
  selectedSpecId,
  selected,
  onSelect,
  onGoToWorkflow,
  onViewAll,
}: Props) {
  const visibleSpecs = specs.slice(0, 4);
  const hasSpecs = !loading && specs.length > 0;

  return (
    <aside className="panel spec-panel">
      <header className="panel-header panel-header--simple">
        <div className="panel-title">
          <IconBox label="📄" compact /> <h2>Spec</h2>
        </div>
      </header>

      <div className="spec-list">
        {loading && <p className="spec-list-loading">Loading specs…</p>}
        {!loading && specs.length === 0 && <NoSpecsFound onGoToWorkflow={onGoToWorkflow} />}
        {visibleSpecs.map((spec) => (
          <SpecCardButton
            key={spec.id}
            spec={spec}
            selected={spec.id === selectedSpecId}
            onSelect={onSelect}
          />
        ))}
      </div>

      {selected?.content && (
        <div className="spec-content">
          <pre>{selected.content}</pre>
        </div>
      )}

      {hasSpecs && (
        <button className="link-button" onClick={onViewAll}>
          View all specs <span>→</span>
        </button>
      )}
    </aside>
  );
}
