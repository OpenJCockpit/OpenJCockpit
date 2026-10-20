import { cx } from '@openjcockpit/ui';
import styles from './AmbientBackdrop.module.scss';

export function AmbientBackdrop() {
  return (
    <>
      <div className={cx(styles.ambient, styles.ambientBlue)} />
      <div className={cx(styles.ambient, styles.ambientPurple)} />
      <div className={styles.scanlines} />
    </>
  );
}
