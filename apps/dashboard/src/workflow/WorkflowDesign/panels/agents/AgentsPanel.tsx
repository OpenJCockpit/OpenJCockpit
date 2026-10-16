import { type ReactNode, useEffect, useState } from 'react';
import { StatusMessage } from '../../../../components/StatusMessage/StatusMessage';
import { Button } from '../../../../ui/Button/Button';
import { getAgentCatalog } from '../../../workflowApi';
import type { AgentCatalog, AgentCatalogStatus } from '../../../workflowTypes';
import { LocalAgentsTree } from './LocalAgentsTree';
import { ProviderAgentsTable } from './ProviderAgentsTable';
import WorkflowDesignStyles from '../../WorkflowDesign.module.scss';

const STATUS_TEXT: Record<Exclude<AgentCatalogStatus, 'OK'>, string> = {
  NOT_CONFIGURED: 'Not configured',
  UNAVAILABLE: 'Unavailable',
  UNAUTHORIZED: 'Access denied',
  INVALID: 'Invalid response',
};

/**
 * Read-only agent catalog: the repository's /agents folder ("Local", grouped by subfolder) and
 * one section per A2A provider, headed by the provider's name (e.g. "LiteLLM").
 */
export function AgentsPanel() {
  const [catalog, setCatalog] = useState<AgentCatalog | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    let cancelled = false;
    getAgentCatalog()
      .then((c) => {
        if (!cancelled) {
          setCatalog(c);
          setError(null);
        }
      })
      .catch((err: unknown) => {
        console.warn(err);
        if (!cancelled) setError('Failed to load the agent catalog.');
      });
    return () => {
      cancelled = true;
    };
  }, [reload]);

  if (error) {
    return (
      <div>
        <StatusMessage variant="error">{error}</StatusMessage>
        <Button variant="secondary" onClick={() => setReload((n) => n + 1)}>
          Retry
        </Button>
      </div>
    );
  }
  if (!catalog) return <StatusMessage variant="loading">Loading agents...</StatusMessage>;

  return (
    <div>
      <div className={WorkflowDesignStyles.workflowDesignToolbar}>
        <span className={WorkflowDesignStyles.workflowDesignToolbarSpacer} />
        <Button variant="secondary" onClick={() => setReload((n) => n + 1)}>
          ↻ Refresh
        </Button>
      </div>

      <CatalogSection
        title={catalog.local.name}
        count={catalog.local.agents.length}
        status={catalog.local.status}
        message={catalog.local.message}
        empty="No agents in the /agents folder yet."
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
          empty={`No agents registered at ${provider.name}.`}
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
  const ok = status === 'OK';
  return (
    <section className={WorkflowDesignStyles.agentCatalogSection} aria-label={title}>
      <h3 className={WorkflowDesignStyles.agentCatalogHeader}>
        {title}
        <span className={WorkflowDesignStyles.agentCatalogCount}>{count}</span>
        {!ok && (
          <span className={WorkflowDesignStyles.agentCatalogStatus}>{STATUS_TEXT[status]}</span>
        )}
      </h3>
      {message && <StatusMessage variant={ok ? 'info' : 'error'}>{message}</StatusMessage>}
      {ok && count === 0 && <StatusMessage>{empty}</StatusMessage>}
      {count > 0 && children}
    </section>
  );
}
