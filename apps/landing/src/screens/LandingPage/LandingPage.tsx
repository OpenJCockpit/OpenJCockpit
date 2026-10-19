import { useMemo, useState } from 'react';
import type Keycloak from 'keycloak-js';
import type { User } from '../../auth/keycloak';
import PuffsBackground from '../../ui/backgrounds/PuffsBackground';
import RevealGroup from '../../ui/motion/RevealGroup';
import Reveal from '../../ui/motion/Reveal';
import TopBar from '../../components/TopBar/TopBar';
import PortalOrb from '../../components/PortalOrb/PortalOrb';
import type { Portal } from '../../components/PortalOrb/PortalOrb';
import IntroGreeting from '../IntroGreeting/IntroGreeting';
import './LandingPage.scss';

const portals: Portal[] = [
  {
    id: 'business',
    title: 'Business',
    subtitle: 'Strategy, dashboarding and decision-making',
    side: 'left',
  },
  {
    id: 'implementation',
    title: 'Implementation',
    subtitle: 'Rollout, configuration and operational actions',
    side: 'right',
  },
];

type LandingPageProps = { user: User; keycloak: Keycloak };

export default function LandingPage({ user, keycloak }: LandingPageProps) {
  const [introVisible, setIntroVisible] = useState(true);

  const firstName = useMemo(() => user?.firstName || 'Tony', [user]);

  return (
    <main className="app-shell">
      <PuffsBackground
        color="hsl(184 100% 58% / 0.26)"
        quantity={85}
        padding={0}
        xOffset={[0, 80]}
        yOffset={[-10, -90]}
        radiusOffset={[1, 9]}
      />

      <div className="ambient ambient-blue" />
      <div className="ambient ambient-purple" />
      <div className="scanlines" />

      <TopBar user={user} keycloak={keycloak} />

      <RevealGroup
        as="section"
        className="hero"
        aria-labelledby="landing-title"
        stagger={0.16}
        data-testid="landing-hero"
      >
        <Reveal as="div" className="hero-copy" duration={0.55} index={0} from={{ y: 24 }}>
          <span className="eyebrow">Authenticated destination</span>
          <h2 id="landing-title">Choose your workspace</h2>
          <p>
            Welcome back, {firstName}. Your token is valid and this hub is now unlocked. Navigate
            directly to Business or Implementation.
          </p>
        </Reveal>

        <div className="orb-stage">
          {portals.map((portal) => (
            <PortalOrb key={portal.id} portal={portal} />
          ))}
        </div>
      </RevealGroup>

      {introVisible && (
        <IntroGreeting firstName={firstName} onDone={() => setIntroVisible(false)} />
      )}
    </main>
  );
}
