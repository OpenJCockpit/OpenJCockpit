import './AgentSequencer.scss';
import React, { useEffect, useRef, useState } from 'react';
import type {
  AgentCard,
  AgentDefinition,
  AgentEvent,
  SequencerNode,
  StatusKind,
} from '../../types';
import { StatusChip } from '../StatusChip/StatusChip';
import { Tooltip } from '../Tooltip/Tooltip';

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
  const [sequence, setSequence] = useState<SequencerNode[]>(nodes);
  const [dragIdx, setDragIdx] = useState<number | null>(null);
  const dragFromRef = useRef<number | null>(null);
  const definitionsApplied = useRef(false);

  useEffect(() => {
    if (definitions.length > 0 && !definitionsApplied.current) {
      definitionsApplied.current = true;
      if (nodes.some((n) => n.kind === 'orb')) {
        // WorkflowExecution has already interleaved agent cards and anchored orb cards
        // into the correct order; re-sorting by sequenceOrder would push orb nodes
        // (whose ids are not in the definitions orderMap) to the end.
        setSequence(nodes);
        return;
      }
      const orderMap = new Map(definitions.map((d) => [d.id, d.sequenceOrder]));
      setSequence(
        [...nodes].sort((a, b) => (orderMap.get(a.id) ?? 99) - (orderMap.get(b.id) ?? 99)),
      );
      return;
    }
    if (!definitionsApplied.current) {
      setSequence(nodes);
      return;
    }
    const nodeMap = new Map(nodes.map((n) => [n.id, n]));
    setSequence((prev) => prev.map((n) => nodeMap.get(n.id) ?? n));
  }, [nodes, definitions]);

  function handleDragStart(idx: number) {
    dragFromRef.current = idx;
    setDragIdx(idx);
  }

  function handleDragOver(e: React.DragEvent, overIdx: number) {
    e.preventDefault();
    const fromIdx = dragFromRef.current;
    if (fromIdx === null || fromIdx === overIdx) return;
    setSequence((prev) => {
      const next = [...prev];
      const [moved] = next.splice(fromIdx, 1);
      next.splice(overIdx, 0, moved);
      return next;
    });
    dragFromRef.current = overIdx;
    setDragIdx(overIdx);
  }

  function handleDragEnd() {
    dragFromRef.current = null;
    setDragIdx(null);
  }

  return (
    <div className="agent-sequencer" aria-label="Agent pipeline sequence">
      {sequence.map((node, index) => {
        const isActive = node.statusKind === 'active';
        const stateClass = `sequencer-node${dragIdx === index ? ' is-dragging' : ''}${isActive ? ' sequencer-node--active' : ''}`;

        if (node.kind === 'orb') {
          const orbLabel = `Workflow orb: ${node.referencedWorkflowName}, ${node.mode === 'SEQUENTIAL' ? 'sequential' : 'parallel'}`;
          return (
            <React.Fragment key={node.id}>
              {index > 0 && <div className="sequencer-connector" aria-hidden="true" />}
              <Tooltip
                content={
                  <>
                    <span className="sequencer-tooltip-status">{node.status}</span>
                    <span className="sequencer-tooltip-text">
                      {node.mode} · workflow: {node.referencedWorkflowName}
                    </span>
                  </>
                }
              >
                <div
                  className={`${stateClass} sequencer-node--orb`}
                  role="status"
                  aria-label={orbLabel}
                >
                  <div className="sequencer-icon">⬢</div>
                  <div className="sequencer-meta">
                    <strong>{node.referencedWorkflowName}</strong>
                    <span className="sequencer-orb-mode">
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
            {index > 0 && <div className="sequencer-connector" aria-hidden="true" />}
            <Tooltip
              content={
                <>
                  <span className="sequencer-tooltip-status">{label}</span>
                  <span className="sequencer-tooltip-text">
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
                <div className="sequencer-icon">{AGENT_ICON[agent.id] ?? '⬡'}</div>
                <div className="sequencer-meta">
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
