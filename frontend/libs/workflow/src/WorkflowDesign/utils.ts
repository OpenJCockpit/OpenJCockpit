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

export function describeSourceOutcome(
  source: SkillCatalogSourceStatus,
  t: TFunction<'workflow'>,
): string {
  const name = source.name;
  switch (source.outcome) {
    case 'AUTH_FAILED':
      return t('skills.sourceOutcome.authFailed', {
        name,
        status: source.httpStatus ?? t('skills.sourceOutcome.unknownStatus'),
      });
    case 'TIMEOUT':
      return t('skills.sourceOutcome.timeout', { name });
    case 'UNREACHABLE':
      return t('skills.sourceOutcome.unreachable', { name });
    case 'INVALID_RESPONSE':
      return t('skills.sourceOutcome.invalidResponse', { name });
    case 'BLOCKED_BY_POLICY':
      return t('skills.sourceOutcome.blockedByPolicy', { name });
    case 'CONFIG_ERROR':
      return t('skills.sourceOutcome.configError', { name });
    default:
      return t('skills.sourceOutcome.unknown', { name });
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
