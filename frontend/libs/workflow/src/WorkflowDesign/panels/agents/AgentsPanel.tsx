import type { ReactNode } from 'react';
import { useTranslation } from '@openjcockpit/i18n';
import { StatusMessage, Button } from '@openjcockpit/ui';
import type { AgentCatalogStatus } from '@openjcockpit/api';
import { useAgentCatalog } from './useAgentCatalog';
import { LocalAgentsTree } from './LocalAgentsTree';
import { ProviderAgentsTable } from './ProviderAgentsTable';
import WorkflowDesignStyles from '../../WorkflowDesign.module.scss';

/**
 * Read-only agent catalog: the repository's /agents folder ("Local", grouped by subfolder) and
 * one section per A2A provider, headed by the provider's name (e.g. "LiteLLM").
 */
export function AgentsPanel() {
  const { t } = useTranslation('workflow');
  const { data: catalog, isError, refetch } = useAgentCatalog();

  if (isError) {
    return (
      <div>
        <StatusMessage variant="error">{t('agentsPanel.loadFailed')}</StatusMessage>
        <Button variant="secondary" onClick={() => void refetch()}>
          {t('shared.retry')}
        </Button>
      </div>
    );
  }
  if (!catalog) return <StatusMessage variant="loading">{t('agentsPanel.loading')}</StatusMessage>;

  return (
    <div>
      <div className={WorkflowDesignStyles.workflowDesignToolbar}>
        <span className={WorkflowDesignStyles.workflowDesignToolbarSpacer} />
        <Button variant="secondary" onClick={() => void refetch()}>
          {t('agentsPanel.refresh')}
        </Button>
      </div>

      <CatalogSection
        title={catalog.local.name}
        count={catalog.local.agents.length}
        status={catalog.local.status}
        message={catalog.local.message}
        empty={t('agentsPanel.emptyLocal')}
      >
        <LocalAgentsTree agents={catalog.local.agents} />
      </CatalogSection>

      {catalog.providers.map((provider) => (
        <CatalogSection
          key={`${provider.type}:${provider.name}`}
          title={provider.name}
          count={provider.agents.length}
          status={provider.status}
          message={provider.message}
          empty={t('agentsPanel.emptyProvider', { name: provider.name })}
        >
          <ProviderAgentsTable provider={provider} agents={provider.agents} />
        </CatalogSection>
      ))}
    </div>
  );
}

interface CatalogSectionProps {
  title: string;
  count: number;
  status: AgentCatalogStatus;
  message?: string | null;
  empty: string;
  children: ReactNode;
}

function CatalogSection({ title, count, status, message, empty, children }: CatalogSectionProps) {
  const { t } = useTranslation('workflow');
  const ok = status === 'OK';
  return (
    <section className={WorkflowDesignStyles.agentCatalogSection} aria-label={title}>
      <h3 className={WorkflowDesignStyles.agentCatalogHeader}>
        {title}
        <span className={WorkflowDesignStyles.agentCatalogCount}>{count}</span>
        {!ok && (
          <span className={WorkflowDesignStyles.agentCatalogStatus}>
            {t(`agentsPanel.status.${status}`)}
          </span>
        )}
      </h3>
      {message && <StatusMessage variant={ok ? 'info' : 'error'}>{message}</StatusMessage>}
      {ok && count === 0 && <StatusMessage>{empty}</StatusMessage>}
      {count > 0 && children}
    </section>
  );
}
