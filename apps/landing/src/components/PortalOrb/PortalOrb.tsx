import Reveal from '../../ui/motion/Reveal';
import './PortalOrb.scss';

export type Portal = {
  id: string;
  title: string;
  subtitle: string;
  side: 'left' | 'right';
};

export default function PortalOrb({ portal }: { portal: Portal }) {
  const isBusiness = portal.id === 'business';

  return (
    <Reveal
      as="button"
      type="button"
      className={`portal-orb ${portal.id}`}
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
      <span className="portal-orb-inner">
        <span className="orb-halo" />
        <span className="orb-core">
          <span className="orb-grid" />
          <span className="orb-title">{portal.title}</span>
          <span className="orb-subtitle">{portal.subtitle}</span>
        </span>
        <span className="orb-status">{isBusiness ? '' : ''}</span>
      </span>
    </Reveal>
  );
}
