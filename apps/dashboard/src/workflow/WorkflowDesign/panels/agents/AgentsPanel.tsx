import { type ReactNode, useEffect, useState } from 'react';
import { getAgentCatalog } from '../../../workflowApi';
import type { AgentCatalog, AgentCatalogStatus } from '../../../workflowTypes';
import { LocalAgentsTree } from './LocalAgentsTree';
import { ProviderAgentsTable } from './ProviderAgentsTable';
import { useState } from 'react';
import { ToolbarButton } from '../../../../components/ToolbarButton/ToolbarButton';
import { StatusMessage } from '../../../../components/StatusMessage/StatusMessage';
import {
  createAgentSpec,
  createSubagentSpec,
  deleteAgentSpec,
  generateAgentSpec,
  generateSubagentSpec,
  listAgentSpecs,
  listSubagentSpecs,
  updateAgentSpec,
} from '../../../workflowApi';
import type { AgentSpec } from '../../../workflowTypes';
import { PromptGenerator } from '../shared/PromptGenerator';
import { AgentEditor } from './AgentEditor';
import { AgentsTable } from './AgentsTable';
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
  const [info, setInfo] = useState<string | null>(null);

  if (!loaded) {
    void listAgentSpecs().then((a) => {
      setItems(a);
      setLoaded(true);
    });
    return <StatusMessage variant="loading">Loading agents...</StatusMessage>;
  }

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
        <p className="settings-error">{error}</p>
        <button className="back-button" onClick={() => setReload((n) => n + 1)}>
          Retry
        </button>
      </div>
    );
  }
  if (!catalog) return <div className="auth-loading">Loading agents...</div>;

  return (
    <div className="agent-catalog">
      <div className="workflow-design-toolbar">
        <span className="workflow-design-toolbar__spacer" />
        <button className="back-button" onClick={() => setReload((n) => n + 1)}>
          ↻ Refresh
        </button>
  return (
    <div>
      <PromptGenerator kind="agent" onGenerate={handleGenerate} />
      <div className={WorkflowDesignStyles.workflowDesignToolbar}>
        <ToolbarButton onClick={startCreate}>+ New agent</ToolbarButton>
      </div>
      {error && <StatusMessage variant="error">{error}</StatusMessage>}
      {info && <StatusMessage>{info}</StatusMessage>}

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
    <section className="agent-catalog__section" aria-label={title}>
      <h3 className="agent-catalog__header">
        {title}
        <span className="agent-catalog__count">{count}</span>
        {!ok && <span className="agent-catalog__status">{STATUS_TEXT[status]}</span>}
      </h3>
      {message && <p className={ok ? 'settings-desc' : 'settings-error'}>{message}</p>}
      {ok && count === 0 && <p className="settings-desc">{empty}</p>}
      {count > 0 && children}
    </section>
  );
}
