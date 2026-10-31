import type { ElementType, HTMLAttributes, TableHTMLAttributes } from 'react';
import { cx } from '../cx';
import styles from './DataTable.module.scss';

/**
 * Full-width table with the dashboard's header and cell styling. It scrolls horizontally inside
 * its own wrapper so a wide table never pushes the page past a narrow viewport.
 */
export function DataTable({ className, ...rest }: TableHTMLAttributes<HTMLTableElement>) {
  return (
    <div className={styles.scroll}>
      <table className={cx(styles.table, className)} {...rest} />
    </div>
  );
}

interface DataTableRowProps extends HTMLAttributes<HTMLTableRowElement> {
  /** Dims the whole row, e.g. for deactivated records. */
  inactive?: boolean;
}

export function DataTableRow({ inactive, className, ...rest }: DataTableRowProps) {
  return <tr className={cx(inactive && styles.inactive, className)} {...rest} />;
}

type ActionsProps = HTMLAttributes<HTMLElement> & { as?: ElementType };

/** Wrapping row of action buttons, usable as a `<td>` or a `<div>`. */
export function Actions({ as: Tag = 'div', className, ...rest }: ActionsProps) {
  return <Tag className={cx(styles.actions, className)} {...rest} />;
}
