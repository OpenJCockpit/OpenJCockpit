import React, { useMemo, useRef, useState } from 'react';
import type {
  AgentCard,
  AgentDefinition,
  AgentEvent,
  SequencerNode,
  StatusKind,
} from '@openjcockpit/api';
import { StatusChip, Tooltip, cx } from '@openjcockpit/ui';
import styles from './AgentSequencer.module.scss';

const AGENT_ICON: Record<string, string> = {
  requirement: '▣',
  impact: '⌁',
  'test-design': '△',
  implementation: '</>',
  review: '✓',
  evidence: '▤',
};

function compactStatus(agent: AgentCard): { label: string; kind: StatusKind } {
  if (agent.statusKind === 'active') return { label: 'Active', kind: 'active' };
  if (agent.statusKind === 'done' || agent.statusKind === 'review') {
    return { label: 'Ready', kind: 'done' };
  }
  return { label: 'Waiting', kind: 'waiting' };
}

interface Props {
  nodes: SequencerNode[];
  definitions: AgentDefinition[];
  latestEvents?: Record<string, AgentEvent>;
}

export function AgentSequencer({ nodes, definitions, latestEvents = {} }: Props) {
  const [userOrder, setUserOrder] = useState<string[] | null>(null);
  const [dragIdx, setDragIdx] = useState<number | null>(null);
  const dragFromRef = useRef<number | null>(null);

  // WorkflowExecution has already interleaved agent cards and anchored orb cards into the
  // correct order; re-sorting by sequenceOrder would push orb nodes (whose ids are not in
  // the definitions orderMap) to the end, so only sort when there are no orbs.
  const baseSequence = useMemo(() => {
    if (definitions.length === 0 || nodes.some((n) => n.kind === 'orb')) return nodes;
    const orderMap = new Map(definitions.map((d) => [d.id, d.sequenceOrder]));
    return [...nodes].sort((a, b) => (orderMap.get(a.id) ?? 99) - (orderMap.get(b.id) ?? 99));
  }, [nodes, definitions]);

  const sequence = useMemo(() => {
    if (!userOrder) return baseSequence;
    const rank = new Map(userOrder.map((id, i) => [id, i]));
    return [...baseSequence].sort(
      (a, b) => (rank.get(a.id) ?? userOrder.length) - (rank.get(b.id) ?? userOrder.length),
    );
  }, [baseSequence, userOrder]);

  function handleDragStart(idx: number) {
    dragFromRef.current = idx;
    setDragIdx(idx);
  }

  function handleDragOver(e: React.DragEvent, overIdx: number) {
    e.preventDefault();
    const fromIdx = dragFromRef.current;
    if (fromIdx === null || fromIdx === overIdx) return;
    const ids = sequence.map((n) => n.id);
    const [moved] = ids.splice(fromIdx, 1);
    if (moved) ids.splice(overIdx, 0, moved);
    setUserOrder(ids);
    dragFromRef.current = overIdx;
    setDragIdx(overIdx);
  }

  function handleDragEnd() {
    dragFromRef.current = null;
    setDragIdx(null);
  }

  return (
    <div className={styles.agentSequencer} aria-label="Agent pipeline sequence">
      {sequence.map((node, index) => {
        const isActive = node.statusKind === 'active';
        // `is-dragging` carries no styles: the old rule keyed off a `.sequencer-item` wrapper that never existed.
        const stateClass = cx(
          styles.sequencerNode,
          dragIdx === index && 'is-dragging',
          isActive && styles.sequencerNodeActive,
        );

        if (node.kind === 'orb') {
          const orbLabel = `Workflow orb: ${node.referencedWorkflowName}, ${node.mode === 'SEQUENTIAL' ? 'sequential' : 'parallel'}`;
          return (
            <React.Fragment key={node.id}>
              {index > 0 && <div className={styles.sequencerConnector} aria-hidden="true" />}
              <Tooltip
                content={
                  <>
                    <span className={styles.sequencerTooltipStatus}>{node.status}</span>
                    <span className={styles.sequencerTooltipText}>
                      {node.mode} · workflow: {node.referencedWorkflowName}
                    </span>
                  </>
                }
              >
                <div
                  className={cx(stateClass, styles.sequencerNodeOrb)}
                  role="status"
                  aria-label={orbLabel}
                >
                  <div className={styles.sequencerIcon}>⬢</div>
                  <div className={styles.sequencerMeta}>
                    <strong>{node.referencedWorkflowName}</strong>
                    <span className={styles.sequencerOrbMode}>
                      {node.mode === 'SEQUENTIAL' ? 'sequential' : 'parallel'}
                    </span>
                  </div>
                  <StatusChip label={node.status} kind={node.statusKind} />
                </div>
              </Tooltip>
            </React.Fragment>
          );
        }

        const agent = node;
        const { label, kind } = compactStatus(agent);
        const event = latestEvents[agent.id];
        return (
          <React.Fragment key={agent.id}>
            {index > 0 && <div className={styles.sequencerConnector} aria-hidden="true" />}
            <Tooltip
              content={
                <>
                  <span className={styles.sequencerTooltipStatus}>{label}</span>
                  <span className={styles.sequencerTooltipText}>
                    {event ? event.title : agent.description}
                  </span>
                </>
              }
            >
              <div
                className={stateClass}
                draggable
                role="button"
                tabIndex={0}
                onDragStart={() => handleDragStart(index)}
                onDragOver={(e) => handleDragOver(e, index)}
                onDragEnd={handleDragEnd}
              >
                <div className={styles.sequencerIcon}>{AGENT_ICON[agent.id] ?? '⬡'}</div>
                <div className={styles.sequencerMeta}>
                  <strong>{agent.name}</strong>
                </div>
                <StatusChip label={label} kind={kind} />
              </div>
            </Tooltip>
          </React.Fragment>
        );
      })}
    </div>
  );
}
