import './AgentLogConsole.scss';
import { useEffect, useRef } from 'react';
import type { AgentEvent } from '../../types';

interface Props {
  events: AgentEvent[];
  agentName: string | null;
  isActive: boolean;
}

function formatTimestamp(timestamp: string): string {
  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) return timestamp;
  return date.toLocaleTimeString('en-GB', { hour12: false });
}

function formatLine(event: AgentEvent): string {
  return `[${formatTimestamp(event.timestamp)}] ${event.status} — ${event.title}`;
}

export function AgentLogConsole({ events, agentName, isActive }: Props) {
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const content =
    events.length > 0 ? events.map(formatLine).join('\n') : '// waiting for agent output…';

  useEffect(() => {
    const el = textareaRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [content]);

  return (
    <div className={`agent-log-console${isActive ? ' agent-log-console--active' : ''}`}>
      <span className="agent-log-console__label">AGENT LOG // {agentName ?? 'IDLE'}</span>
      <textarea
        ref={textareaRef}
        className="agent-log-console__output"
        readOnly
        value={content}
        aria-label="Agent execution log"
      />
    </div>
  );
}
