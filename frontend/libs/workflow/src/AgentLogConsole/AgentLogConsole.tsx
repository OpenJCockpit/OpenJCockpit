import { useEffect, useRef } from 'react';
import type { AgentEvent } from '@openjcockpit/api';
import { useFormatters, useTranslation } from '@openjcockpit/i18n';
import styles from './AgentLogConsole.module.scss';

interface Props {
  events: AgentEvent[];
  agentName: string | null;
  isActive: boolean;
}

// The console stays on a 24-hour clock whatever the locale; h23 avoids a "24:00:00" midnight.
const LOG_TIME_FORMAT: Intl.DateTimeFormatOptions = {
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
};

export function AgentLogConsole({ events, agentName, isActive }: Props) {
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const { formatTime } = useFormatters();
  const { t } = useTranslation('workflow');

  function formatTimestamp(timestamp: string): string {
    if (Number.isNaN(new Date(timestamp).getTime())) return timestamp;
    return formatTime(timestamp, LOG_TIME_FORMAT);
  }

  function formatLine(event: AgentEvent): string {
    return `[${formatTimestamp(event.timestamp)}] ${event.status} — ${event.title}`;
  }

  const content =
    events.length > 0 ? events.map(formatLine).join('\n') : t('agentLogConsole.waiting');

  useEffect(() => {
    const el = textareaRef.current!;
    el.scrollTop = el.scrollHeight;
  }, [content]);

  return (
    <div className={styles.agentLogConsole} data-active={isActive ? '' : undefined}>
      <span className={styles.agentLogConsoleLabel}>
        {t('agentLogConsole.label', { agent: agentName ?? t('agentLogConsole.idle') })}
      </span>
      <textarea
        ref={textareaRef}
        className={styles.agentLogConsoleOutput}
        readOnly
        value={content}
        aria-label={t('agentLogConsole.ariaLabel')}
      />
    </div>
  );
}
