import type { SpecFile } from '@openjcockpit/api';
import { useTranslation } from '@openjcockpit/i18n';
import {
  IconBox,
  Panel,
  PanelHeader,
  PanelTitle,
  Button,
  specStyles,
  feedbackErrorClass,
} from '@openjcockpit/ui';
import { NoSpecsFound } from './NoSpecsFound';
import { SpecCardButton } from './SpecCardButton';
import styles from './SpecPanel.module.scss';

interface Props {
  specs: SpecFile[];
  loading: boolean;
  error?: boolean;
  onRetry?: () => void;
  selectedSpecId: string;
  selected: SpecFile | undefined;
  onSelect: (id: string) => void;
  onGoToWorkflow: () => void;
  onViewAll: () => void;
}

export function SpecPanel({
  specs,
  loading,
  error = false,
  onRetry,
  selectedSpecId,
  selected,
  onSelect,
  onGoToWorkflow,
  onViewAll,
}: Props) {
  const { t } = useTranslation('dashboard');
  const visibleSpecs = specs.slice(0, 4);
  const hasSpecs = !loading && specs.length > 0;

  return (
    <Panel as="aside" className={specStyles.specPanel}>
      <PanelHeader simple>
        <PanelTitle>
          <IconBox label="📄" compact /> <h2>{t('specPanel.title')}</h2>
        </PanelTitle>
      </PanelHeader>

      <div className={specStyles.specList}>
        {loading && <p className={specStyles.specListLoading}>{t('specPanel.loading')}</p>}
        {!loading && error && (
          <div role="alert" data-testid="specs-error">
            <p className={feedbackErrorClass}>{t('specPanel.error')}</p>
            {onRetry && <Button onClick={onRetry}>{t('specPanel.retry')}</Button>}
          </div>
        )}
        {!loading && !error && specs.length === 0 && (
          <NoSpecsFound onGoToWorkflow={onGoToWorkflow} />
        )}
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
        <Button variant="link" onClick={onViewAll}>
          {t('specPanel.viewAll')} <span>→</span>
        </Button>
      )}
    </Panel>
  );
}
