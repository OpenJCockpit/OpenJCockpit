import styles from './Backdrop.module.scss';

/** Decorative glow and grid behind a full-page view. Needs a positioned, isolated parent. */
export function Backdrop() {
  return (
    <>
      <div className={styles.neuralBg} aria-hidden="true" />
      <div className={`${styles.orb} ${styles.orbLeft}`} aria-hidden="true" />
      <div className={`${styles.orb} ${styles.orbRight}`} aria-hidden="true" />
    </>
  );
}
