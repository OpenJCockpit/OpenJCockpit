import { Fragment, useState } from 'react';
import { getProviderAgentCard } from '@openjcockpit/api';
import type { A2AAgentCard, ProviderCatalogAgent } from '@openjcockpit/api';
import { StatusMessage, Button, DataTable } from '@openjcockpit/ui';
import WorkflowDesignStyles from '../../WorkflowDesign.module.scss';

interface ProviderAgentsTableProps {
  provider: { name: string; type: string };
  agents: ProviderCatalogAgent[];
}

type CardState = { loading: true } | { card: A2AAgentCard } | { error: string };

export function ProviderAgentsTable({ provider, agents }: ProviderAgentsTableProps) {
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
          [agent.name]: { error: `Could not retrieve the agent card from ${provider.name}.` },
        }));
      });
  }

  return (
    <DataTable>
      <thead>
        <tr>
          <th>Name</th>
          <th>Description</th>
          <th>Version</th>
          <th>A2A</th>
          <th>Endpoint</th>
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
                {agent.streaming && ' · streaming'}
              </td>
              <td className={WorkflowDesignStyles.agentCatalogPath}>
                {agent.url}
                {agent.backendUrl && <div>routes to {agent.backendUrl}</div>}
              </td>
              <td>
                <Button
                  variant="secondary"
                  aria-expanded={open === agent.name}
                  onClick={() => toggle(agent)}
                >
                  {open === agent.name ? 'Hide' : 'Details'}
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
  if (!state || 'loading' in state) {
    return <StatusMessage role="status">Loading agent card...</StatusMessage>;
  }
  if ('error' in state) return <StatusMessage variant="error">{state.error}</StatusMessage>;
  const { card } = state;
  return (
    <div
      className={WorkflowDesignStyles.agentCatalogDetails}
      aria-label={`Agent card ${card.name}`}
    >
      <dl>
        <dt>Endpoint</dt>
        <dd>{card.url}</dd>
        <dt>Agent card</dt>
        <dd>
          {cardUrl}{' '}
          <span className={WorkflowDesignStyles.agentCatalogId}>(provider key required)</span>
        </dd>
        <dt>Protocol</dt>
        <dd>
          A2A {card.protocolVersion}
          {card.preferredTransport && ` · ${card.preferredTransport}`}
        </dd>
        <dt>Capabilities</dt>
        <dd>
          streaming {card.capabilities?.streaming ? 'yes' : 'no'} · push notifications{' '}
          {card.capabilities?.pushNotifications ? 'yes' : 'no'}
        </dd>
        <dt>Input / output</dt>
        <dd>
          {(card.defaultInputModes ?? []).join(', ')} → {(card.defaultOutputModes ?? []).join(', ')}
        </dd>
      </dl>
      {(card.skills ?? []).length > 0 && (
        <ul className={WorkflowDesignStyles.agentCatalogSkills}>
          {card.skills!.map((skill) => (
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
