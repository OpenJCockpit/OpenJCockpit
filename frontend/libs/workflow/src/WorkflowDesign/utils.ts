import type {
  AgentDefinition,
  SkillCatalogSourceStatus,
  WorkflowExportBundle,
} from '@openjcockpit/api';
import { referenceLabel } from '@openjcockpit/api';
import type { TFunction } from '@openjcockpit/i18n';

export function toList(value: string): string[] {
  return value
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
}

export function readFileText(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error);
    reader.readAsText(file);
  });
}

export function downloadWorkflowBundle(bundle: WorkflowExportBundle) {
  const blob = new Blob([JSON.stringify(bundle, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = 'workflow-export.json';
  link.click();
  URL.revokeObjectURL(url);
}

export function describeSourceOutcome(source: SkillCatalogSourceStatus): string {
  switch (source.outcome) {
    case 'AUTH_FAILED':
      return `${source.name}: authentication was rejected (HTTP ${source.httpStatus ?? 'unknown'}).`;
    case 'TIMEOUT':
      return `${source.name}: timed out.`;
    case 'UNREACHABLE':
      return `${source.name}: could not be reached.`;
    case 'INVALID_RESPONSE':
      return `${source.name}: returned an invalid response.`;
    case 'BLOCKED_BY_POLICY':
      return `${source.name}: blocked by egress policy.`;
    case 'CONFIG_ERROR':
      return `${source.name}: configuration error — check its API key.`;
    default:
      return `${source.name}: unknown status.`;
  }
}

// Agents currently linked to the workflow, in the workflow's execution order — the gate and orb
// placement dropdowns offer exactly these and nothing else (AC-02). A linked reference that is not
// (or no longer) in the catalog is still offered, labelled by its reference.
export function selectedStagesInOrder(
  availableAgents: AgentDefinition[],
  agentIds: string[],
  t: TFunction<'api'>,
): AgentDefinition[] {
  const byId = new Map(availableAgents.map((agent) => [agent.id, agent]));
  return agentIds.map(
    (id, index) =>
      byId.get(id) ?? {
        id,
        name: referenceLabel(id, t),
        description: '',
        role: '',
        sequenceOrder: index,
        inputType: '',
        outputType: '',
      },
  );
}
