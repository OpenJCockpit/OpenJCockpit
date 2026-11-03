import type { SpecFile } from '@openjcockpit/api';
import { useTranslation } from '@openjcockpit/i18n';
import {
  PanelHeader,
  PanelTitle,
  Button,
  specStyles,
  feedbackErrorClass,
  Icon,
} from '@openjcockpit/ui';
import { NoSpecsFound } from './NoSpecsFound';
import { SpecCardButton } from './SpecCardButton';
import styles from './SpecPanel.module.scss';

const MAX_VISIBLE = 4;

interface Props {
  specs: SpecFile[];
  loading: boolean;
  error?: boolean;
  onRetry?: () => void;
  selectedSpecId: string;
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
  onSelect,
  onGoToWorkflow,
  onViewAll,
}: Props) {
  const { t } = useTranslation('dashboard');
  const visibleSpecs = specs.slice(0, MAX_VISIBLE);
  const hasSpecs = !loading && specs.length > 0;

  return (
    <aside className={styles.specColumn}>
      <PanelHeader>
        <PanelTitle>
          <h2>{t('specPanel.title')}</h2>
        </PanelTitle>
      </PanelHeader>

      <div className={specStyles.specList}>
        {loading && (
          <div role="status" aria-label={t('specPanel.loading')}>
            <div className={styles.skeleton} />
          </div>
        )}
        {!loading && error && (
          <div role="alert" data-testid="specs-error" className={styles.specError}>
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

      {hasSpecs && specs.length > MAX_VISIBLE && (
        <Button variant="link" onClick={onViewAll}>
          {t('specPanel.viewAllCount', { count: specs.length })} <Icon name="arrow-right" />
        </Button>
      )}
    </aside>
  );
}
