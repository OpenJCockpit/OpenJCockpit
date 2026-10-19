import { cx } from '../cx';
import styles from './AmbientBackdrop.module.scss';

export default function AmbientBackdrop() {
  return (
    <>
      <div className={cx(styles.ambient, styles.ambientBlue)} />
      <div className={cx(styles.ambient, styles.ambientPurple)} />
      <div className={styles.scanlines} />
    </>
  );
}
