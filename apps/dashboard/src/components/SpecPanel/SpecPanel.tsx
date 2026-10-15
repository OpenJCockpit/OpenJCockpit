import type { SpecFile } from '../../types';
import { IconBox } from '../IconBox/IconBox';
import { NoSpecsFound } from './NoSpecsFound';
import { SpecCardButton } from './SpecCardButton';
import styles from './SpecPanel.module.scss';
import { Panel, PanelHeader, PanelTitle } from '../../ui/Panel/Panel';

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
    <Panel as="aside" className="spec-panel">
      <PanelHeader simple>
        <PanelTitle>
          <IconBox label="📄" compact /> <h2>Spec</h2>
        </PanelTitle>
      </PanelHeader>

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
        <div className={styles.specContent}>
          <pre>{selected.content}</pre>
        </div>
      )}

      {hasSpecs && (
        <button className="link-button" onClick={onViewAll}>
          View all specs <span>→</span>
        </button>
      )}
    </Panel>
  );
}
