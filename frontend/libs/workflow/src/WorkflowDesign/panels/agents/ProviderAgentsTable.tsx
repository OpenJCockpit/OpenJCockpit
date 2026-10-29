import { Fragment, useState } from 'react';
import { getProviderAgentCard } from '@openjcockpit/api';
import type { A2AAgentCard, ProviderCatalogAgent } from '@openjcockpit/api';
import { useTranslation } from '@openjcockpit/i18n';
import { StatusMessage, Button, DataTable } from '@openjcockpit/ui';
import WorkflowDesignStyles from '../../WorkflowDesign.module.scss';

interface ProviderAgentsTableProps {
  provider: { name: string; type: string };
  agents: ProviderCatalogAgent[];
}

type CardState = { loading: true } | { card: A2AAgentCard } | { error: string };

export function ProviderAgentsTable({ provider, agents }: ProviderAgentsTableProps) {
  const { t } = useTranslation('workflow');
  const [open, setOpen] = useState<string | null>(null);
  const [cards, setCards] = useState<Record<string, CardState>>({});

  function toggle(agent: ProviderCatalogAgent) {
    if (open === agent.name) {
      setOpen(null);
      return;
    }
    setOpen(agent.name);
    setCards((prev) => ({ ...prev, [agent.name]: { loading: true } }));
    getProviderAgentCard(provider.type, agent.name)
      .then((card) => setCards((prev) => ({ ...prev, [agent.name]: { card } })))
      .catch((err: unknown) => {
        console.warn(err);
        setCards((prev) => ({
          ...prev,
          [agent.name]: { error: t('providerAgents.cardFailed', { name: provider.name }) },
        }));
      });
  }

  return (
    <DataTable>
      <thead>
        <tr>
          <th>{t('shared.name')}</th>
          <th>{t('shared.description')}</th>
          <th>{t('shared.version')}</th>
          <th>{t('providerAgents.a2a')}</th>
          <th>{t('providerAgents.endpoint')}</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        {agents.map((agent) => (
          <Fragment key={agent.id}>
            <tr>
              <td>{agent.name}</td>
              <td>{agent.description}</td>
              <td>{agent.version}</td>
              <td>
                {agent.protocolVersion}
                {agent.streaming && t('providerAgents.streaming')}
              </td>
              <td className={WorkflowDesignStyles.agentCatalogPath}>
                {agent.url}
                {agent.backendUrl && (
                  <div>{t('providerAgents.routesTo', { url: agent.backendUrl })}</div>
                )}
              </td>
              <td>
                <Button
                  variant="secondary"
                  aria-expanded={open === agent.name}
                  onClick={() => toggle(agent)}
                >
                  {open === agent.name ? t('providerAgents.hide') : t('providerAgents.details')}
                </Button>
              </td>
            </tr>
            {open === agent.name && (
              <tr>
                <td colSpan={6}>
                  <AgentCardDetails state={cards[agent.name]} cardUrl={agent.cardUrl} />
                </td>
              </tr>
            )}
          </Fragment>
        ))}
      </tbody>
    </DataTable>
  );
}

function AgentCardDetails({ state, cardUrl }: { state?: CardState; cardUrl?: string | null }) {
  const { t } = useTranslation('workflow');
  if (!state || 'loading' in state) {
    return <StatusMessage role="status">{t('providerAgents.cardLoading')}</StatusMessage>;
  }
  if ('error' in state) return <StatusMessage variant="error">{state.error}</StatusMessage>;
  const { card } = state;
  const skills = card.skills ?? [];
  return (
    <div
      className={WorkflowDesignStyles.agentCatalogDetails}
      aria-label={t('providerAgents.cardLabel', { name: card.name })}
    >
      <dl>
        <dt>{t('providerAgents.endpoint')}</dt>
        <dd>{card.url}</dd>
        <dt>{t('providerAgents.agentCard')}</dt>
        <dd>
          {cardUrl}{' '}
          <span className={WorkflowDesignStyles.agentCatalogId}>
            {t('providerAgents.providerKeyRequired')}
          </span>
        </dd>
        <dt>{t('providerAgents.protocol')}</dt>
        <dd>
          {t('providerAgents.protocolValue', { version: card.protocolVersion })}
          {card.preferredTransport &&
            t('providerAgents.transport', { transport: card.preferredTransport })}
        </dd>
        <dt>{t('providerAgents.capabilities')}</dt>
        <dd>
          {t('providerAgents.capabilitiesValue', {
            streaming: card.capabilities?.streaming
              ? t('providerAgents.yes')
              : t('providerAgents.no'),
            push: card.capabilities?.pushNotifications
              ? t('providerAgents.yes')
              : t('providerAgents.no'),
          })}
        </dd>
        <dt>{t('providerAgents.io')}</dt>
        <dd>
          {(card.defaultInputModes ?? []).join(', ')} → {(card.defaultOutputModes ?? []).join(', ')}
        </dd>
      </dl>
      {skills.length > 0 && (
        <ul className={WorkflowDesignStyles.agentCatalogSkills}>
          {skills.map((skill) => (
            <li key={skill.id}>
              <strong>{skill.name ?? skill.id}</strong>
              {skill.description && ` — ${skill.description}`}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
