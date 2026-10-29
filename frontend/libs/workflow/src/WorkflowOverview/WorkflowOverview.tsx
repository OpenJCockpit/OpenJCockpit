import { referenceLabel, runStatusKind } from '@openjcockpit/api';
import type { AgentDefinition, Project, WorkflowDefinition } from '@openjcockpit/api';
import { useFormatters, useTranslation, type TFunction } from '@openjcockpit/i18n';
import {
  StatusChip,
  Eyebrow,
  Panel,
  fieldErrorClass,
  fieldHintClass,
  Button,
  StatusMessage,
} from '@openjcockpit/ui';
import { WorkflowPromptDialog } from '../WorkflowPromptDialog/WorkflowPromptDialog';
import { useWorkflowOverview } from './useWorkflowOverview';
import styles from './WorkflowOverview.module.scss';

interface Props {
  project: Project | null;
  onWorkflowStarted?: (workflow: WorkflowDefinition, runId: string) => void;
  onViewExecutionTab?: (workflow: WorkflowDefinition) => void;
}

function triggerSummary(workflow: WorkflowDefinition, t: TFunction<'workflow'>): string {
  const triggers: string[] = [];
  if (workflow.trigger?.dashboardButtonEnabled) triggers.push(t('overview.triggerDashboard'));
  if (workflow.trigger?.hermesSignalEnabled) {
    triggers.push(
      t('overview.triggerHermes', {
        type: workflow.trigger.hermesSignalType ?? t('overview.triggerHermesAny'),
      }),
    );
  }
  if (workflow.trigger?.fileDeliveryEnabled) triggers.push(t('overview.triggerFile'));
  return triggers.length > 0 ? triggers.join(', ') : t('overview.noneConfigured');
}

function agentSummary(
  agentIds: string[],
  index: Map<string, AgentDefinition>,
  degraded: boolean,
  t: TFunction<'workflow'>,
  tApi: TFunction<'api'>,
): string {
  if (agentIds.length === 0) return t('overview.none');
  if (degraded) {
    return agentIds.join(', ');
  }
  // in the workflow's execution order; references missing from the catalog stay visible
  const parts = agentIds.map((id) => {
    const agent = index.get(id);
    return agent ? agent.name : t('overview.unrecognised', { label: referenceLabel(id, tApi) });
  });
  return parts.join(', ');
}

export function WorkflowOverview({ project, onWorkflowStarted, onViewExecutionTab }: Props) {
  const { t } = useTranslation('workflow');
  const { t: tApi } = useTranslation('api');
  const { formatDateTime } = useFormatters();
  const {
    loaded,
    visibleWorkflows,
    agentIndex,
    listError,
    catalogueError,
    opaEnabled,
    decisionSummaries,
    startingId,
    exportingId,
    feedback,
    promptWorkflow,
    setPromptWorkflow,
    handleStart,
    launchWorkflow,
    handleExportDecisionLogs,
  } = useWorkflowOverview(project, onWorkflowStarted);

  if (!loaded) {
    return <StatusMessage variant="loading">{t('overview.loading')}</StatusMessage>;
  }

  return (
    <section className="workflow-overview">
      {promptWorkflow && (
        <WorkflowPromptDialog
          workflowName={promptWorkflow.name}
          busy={startingId === promptWorkflow.id}
          onSubmit={(prompt) => void launchWorkflow(promptWorkflow, { prompt })}
          onCancel={() => setPromptWorkflow(null)}
        />
      )}

      {listError && (
        <p className={fieldErrorClass} role="alert">
          {t('overview.loadFailed', { error: listError })}
        </p>
      )}

      {!listError && visibleWorkflows.length === 0 && (
        <p className={fieldHintClass}>{t('overview.empty')}</p>
      )}

      {catalogueError && (
        <p className={fieldErrorClass} role="alert">
          {catalogueError}
        </p>
      )}

      <div className={styles.workflowCardGrid}>
        {visibleWorkflows.map((workflow) => (
          <Panel as="article" className={styles.workflowCard} key={workflow.id}>
            <header className={styles.workflowCardHeader}>
              <div>
                <Eyebrow>{workflow.id}</Eyebrow>
                <h2>{workflow.name}</h2>
              </div>
              <StatusChip label={workflow.status} kind={runStatusKind(workflow.status ?? '')} />
            </header>

            <dl className={styles.workflowCardMeta}>
              <div>
                <dt>{t('overview.project')}</dt>
                <dd>{workflow.projectName}</dd>
              </div>
              <div>
                <dt>{t('overview.triggers')}</dt>
                <dd>{triggerSummary(workflow, t)}</dd>
              </div>
              <div>
                <dt>{t('overview.agents')}</dt>
                <dd>
                  {agentSummary(workflow.agentIds, agentIndex, catalogueError !== null, t, tApi)}
                </dd>
              </div>
              <div>
                <dt>{t('overview.subagents')}</dt>
                <dd>
                  {workflow.subagentNames.length > 0
                    ? workflow.subagentNames.join(', ')
                    : t('overview.none')}
                </dd>
              </div>
              <div>
                <dt>{t('overview.skills')}</dt>
                <dd>
                  {workflow.skillNames.length > 0
                    ? workflow.skillNames.join(', ')
                    : t('overview.none')}
                </dd>
              </div>
              <div>
                <dt>{t('overview.mcpTools')}</dt>
                <dd>
                  {workflow.mcpTools.length > 0
                    ? workflow.mcpTools.map((tool) => tool.name).join(', ')
                    : t('overview.none')}
                </dd>
              </div>
              <div>
                <dt>{t('overview.lastExecution')}</dt>
                <dd>
                  {workflow.lastExecutionStatus ?? t('overview.neverRun')}
                  {workflow.lastExecutionAt ? ` — ${formatDateTime(workflow.lastExecutionAt)}` : ''}
                </dd>
              </div>
              <div>
                <dt>{t('overview.opa')}</dt>
                <dd>
                  <StatusChip
                    label={opaEnabled ? t('overview.enabled') : t('overview.disabled')}
                    kind={opaEnabled ? 'ok' : 'waiting'}
                  />{' '}
                  {decisionSummaries[workflow.id]?.lastResult
                    ? t('overview.lastDecision', {
                        result: decisionSummaries[workflow.id]?.lastResult,
                        id: decisionSummaries[workflow.id]?.lastDecisionId ?? tApi('notAvailable'),
                      })
                    : t('overview.noDecisions')}
                </dd>
              </div>
              <div>
                <dt>{t('overview.decisions')}</dt>
                <dd>
                  {t('overview.decisionCounts', {
                    allowed: decisionSummaries[workflow.id]?.allowed ?? 0,
                    denied: decisionSummaries[workflow.id]?.denied ?? 0,
                    requiresApproval: decisionSummaries[workflow.id]?.requiresApproval ?? 0,
                  })}
                </dd>
              </div>
            </dl>

            <div className={styles.workflowCardActions}>
              <Button
                variant="primary"
                onClick={() => void handleStart(workflow)}
                disabled={startingId === workflow.id}
              >
                {startingId === workflow.id ? t('overview.starting') : t('overview.start')}
              </Button>
              <Button
                onClick={() => void handleExportDecisionLogs(workflow)}
                disabled={exportingId === workflow.id}
              >
                {exportingId === workflow.id ? t('overview.exporting') : t('overview.export')}
              </Button>
              {onViewExecutionTab && (
                <Button variant="link" onClick={() => onViewExecutionTab(workflow)}>
                  {t('overview.openExecution')} <span>→</span>
                </Button>
              )}
              {feedback[workflow.id] && (
                <span className={styles.workflowCardFeedback}>{feedback[workflow.id]}</span>
              )}
            </div>
          </Panel>
        ))}
      </div>
    </section>
  );
}
