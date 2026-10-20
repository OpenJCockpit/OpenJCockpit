import { Reveal, cx } from '@openjcockpit/ui';
import styles from './PortalOrb.module.scss';

export type PortalId = 'business' | 'implementation';

export type Portal = {
  id: PortalId;
  title: string;
  subtitle: string;
  side: 'left' | 'right';
};

type PortalOrbProps = { portal: Portal; onSelect: (portal: PortalId) => void };

export function PortalOrb({ portal, onSelect }: PortalOrbProps) {
  const isBusiness = portal.id === 'business';

  return (
    <Reveal
      as="button"
      type="button"
      className={cx(styles.portalOrb, isBusiness ? styles.business : styles.implementation)}
      data-testid={`portal-orb-${portal.id}`}
      duration={1.05}
      index={isBusiness ? 1 : 2}
      from={{ y: 34, scale: 0.92 }}
      onClick={() => onSelect(portal.id)}
    >
      <span className={styles.portalOrbInner}>
        <span className={styles.orbHalo} />
        <span className={styles.orbCore}>
          <span className={styles.orbGrid} />
          <span className={styles.orbTitle}>{portal.title}</span>
          <span className={styles.orbSubtitle}>{portal.subtitle}</span>
        </span>
        <span className={styles.orbStatus} />
      </span>
    </Reveal>
  );
}
