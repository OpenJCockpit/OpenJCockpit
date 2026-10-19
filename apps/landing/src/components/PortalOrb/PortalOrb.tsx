import Reveal from '../../ui/motion/Reveal';
import { cx } from '../../ui/cx';
import styles from './PortalOrb.module.scss';

export type Portal = {
  id: string;
  title: string;
  subtitle: string;
  side: 'left' | 'right';
};

export default function PortalOrb({ portal }: { portal: Portal }) {
  const isBusiness = portal.id === 'business';
  const variantClass =
    portal.id === 'business'
      ? styles.business
      : portal.id === 'implementation'
        ? styles.implementation
        : undefined;

  return (
    <Reveal
      as="button"
      type="button"
      className={cx(styles.portalOrb, variantClass)}
      data-testid={`portal-orb-${portal.id}`}
      duration={1.05}
      index={isBusiness ? 1 : 2}
      from={{ y: 34, scale: 0.92 }}
      onClick={() => {
        if (portal.id === 'implementation') {
          window.location.href = 'http://localhost:4000';
        } else if (portal.id === 'business') {
          window.location.href = 'http://localhost:4000/?view=spec-files';
        } else {
          // eslint-disable-next-line no-console -- pre-existing fallback log, behavior frozen in the conversion
          console.info(`${portal.title} opened`);
        }
      }}
    >
      <span className={styles.portalOrbInner}>
        <span className={styles.orbHalo} />
        <span className={styles.orbCore}>
          <span className={styles.orbGrid} />
          <span className={styles.orbTitle}>{portal.title}</span>
          <span className={styles.orbSubtitle}>{portal.subtitle}</span>
        </span>
        <span className={styles.orbStatus}>{isBusiness ? '' : ''}</span>
      </span>
    </Reveal>
  );
}
