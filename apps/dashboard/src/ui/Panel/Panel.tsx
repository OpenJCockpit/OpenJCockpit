import type { ElementType, HTMLAttributes } from 'react';
import { cx } from '../cx';
import styles from './Panel.module.scss';

/** Panel surface class for elements that cannot be a <Panel> (e.g. a ModalShell dialog). */
export const panelClassName = styles.panel;

type PanelProps = HTMLAttributes<HTMLElement> & { as?: ElementType };

/** Glass surface used for every dashboard card. */
export function Panel({ as: Tag = 'section', className, ...rest }: PanelProps) {
  return <Tag className={cx(styles.panel, className)} {...rest} />;
}

interface PanelHeaderProps extends HTMLAttributes<HTMLElement> {
  /** Adds a bottom divider. */
  simple?: boolean;
}

export function PanelHeader({ simple, className, ...rest }: PanelHeaderProps) {
  return (
    <header className={cx(styles.header, simple && styles.headerSimple, className)} {...rest} />
  );
}

export function PanelTitle({ className, ...rest }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cx(styles.title, className)} {...rest} />;
}

/** Small muted label above a heading. */
export function Eyebrow({ className, ...rest }: HTMLAttributes<HTMLSpanElement>) {
  return <span className={cx(styles.eyebrow, className)} {...rest} />;
}
