import type { LocalCatalogAgent } from '../../../workflowTypes';
import { DataTable } from '../../../../ui/DataTable/DataTable';
import { fieldErrorClass } from '../../../../ui/Form/Form';
import WorkflowDesignStyles from '../../WorkflowDesign.module.scss';

interface FolderNode {
  name: string;
  agents: LocalCatalogAgent[];
  children: Map<string, FolderNode>;
}

/** Builds the folder hierarchy below /agents from each agent's folder path. */
export function buildFolderTree(agents: LocalCatalogAgent[]): FolderNode {
  const root: FolderNode = { name: '', agents: [], children: new Map() };
  for (const agent of agents) {
    let node = root;
    for (const folder of agent.folders) {
      let child = node.children.get(folder);
      if (!child) {
        child = { name: folder, agents: [], children: new Map() };
        node.children.set(folder, child);
      }
      node = child;
    }
    node.agents.push(agent);
  }
  return root;
}

export function LocalAgentsTree({ agents }: { agents: LocalCatalogAgent[] }) {
  return <FolderView node={buildFolderTree(agents)} depth={0} />;
}

function FolderView({ node, depth }: { node: FolderNode; depth: number }) {
  const children = [...node.children.values()].sort((a, b) => a.name.localeCompare(b.name));
  return (
    <div className={depth > 0 ? WorkflowDesignStyles.agentCatalogFolder : undefined}>
      {depth > 0 && (
        <h4 className={WorkflowDesignStyles.agentCatalogFolderName} aria-level={3 + depth}>
          📁 {node.name}
        </h4>
      )}
      {node.agents.length > 0 && <LocalAgentsTable agents={node.agents} />}
      {children.map((child) => (
        <FolderView key={child.name} node={child} depth={depth + 1} />
      ))}
    </div>
  );
}

function LocalAgentsTable({ agents }: { agents: LocalCatalogAgent[] }) {
  return (
    <DataTable>
      <thead>
        <tr>
          <th>Name</th>
          <th>Description</th>
          <th>Version</th>
          <th>Model</th>
          <th>Skills</th>
          <th>File</th>
        </tr>
      </thead>
      <tbody>
        {agents.map((agent) => (
          <tr
            key={agent.path}
            className={agent.valid ? undefined : WorkflowDesignStyles.agentCatalogInvalid}
          >
            <td>
              {agent.name}
              {agent.name !== agent.id && (
                <div className={WorkflowDesignStyles.agentCatalogId}>{agent.id}</div>
              )}
            </td>
            <td>
              {agent.valid ? (
                agent.description
              ) : (
                <span className={fieldErrorClass}>{agent.problem}</span>
              )}
            </td>
            <td>{agent.version}</td>
            <td>{agent.model}</td>
            <td>{agent.skills.join(', ')}</td>
            <td className={WorkflowDesignStyles.agentCatalogPath}>agents/{agent.path}</td>
          </tr>
        ))}
      </tbody>
    </DataTable>
  );
}
