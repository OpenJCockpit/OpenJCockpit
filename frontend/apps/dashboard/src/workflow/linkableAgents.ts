import { getAgentCatalog } from '@openjcockpit/api';
import type { AgentCatalog, AgentDefinition } from '@openjcockpit/api';

/**
 * Agents a workflow can link: every valid agent of the central agent catalog (Workflow design →
 * Agents), keyed by its agent reference — `local:<id>` for the repository /agents folder and
 * `a2a:<provider>:<agent>` for an A2A provider. `role` carries the catalog section (Local or the
 * provider name); `sequenceOrder` is only the catalog listing order, the workflow's own agentIds
 * order is its execution order.
 */
export function linkableAgents(catalog: AgentCatalog): AgentDefinition[] {
  const local = catalog.local.agents
    .filter((agent) => agent.valid)
    .map((agent) => ({
      id: `local:${agent.id}`,
      name: agent.name,
      description: agent.description ?? '',
      role: catalog.local.name,
    }));
  const remote = catalog.providers.flatMap((provider) =>
    provider.agents.map((agent) => ({
      id: `a2a:${provider.type}:${agent.name}`,
      name: agent.name,
      description: agent.description ?? '',
      role: provider.name,
    })),
  );
  return [...local, ...remote].map((agent, index) => ({
    ...agent,
    sequenceOrder: index,
    inputType: '',
    outputType: '',
  }));
}

export async function fetchLinkableAgents(): Promise<AgentDefinition[]> {
  return linkableAgents(await getAgentCatalog());
}

/** Like {@link fetchLinkableAgents}, but never fails: labels fall back to the references. */
export async function loadLinkableAgents(): Promise<AgentDefinition[]> {
  try {
    return await fetchLinkableAgents();
  } catch (error) {
    console.warn('Could not load the agent catalog', error);
    return [];
  }
}
