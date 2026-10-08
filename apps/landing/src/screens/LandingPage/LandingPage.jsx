import { useMemo, useState } from 'react';
import PuffsBackground from '../../ui/backgrounds/PuffsBackground.jsx';
import RevealGroup from '../../ui/motion/RevealGroup.jsx';
import Reveal from '../../ui/motion/Reveal.jsx';
import TopBar from '../../components/TopBar/TopBar.jsx';
import PortalOrb from '../../components/PortalOrb/PortalOrb.jsx';
import IntroGreeting from '../IntroGreeting/IntroGreeting.jsx';
import './LandingPage.scss';

const portals = [
  {
    id: 'business',
    title: 'Business',
    subtitle: 'Strategy, dashboarding and decision-making',
    side: 'left'
  },
  {
    id: 'implementation',
    title: 'Implementation',
    subtitle: 'Rollout, configuration and operational actions',
    side: 'right'
  }
];

export default function LandingPage({ user, keycloak }) {
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
        <Reveal
          as="div"
          className="hero-copy"
          duration={0.55}
          index={0}
          from={{ y: 24 }}
        >
          <span className="eyebrow">Authenticated destination</span>
          <h2 id="landing-title">Choose your workspace</h2>
          <p>
            Welcome back, {firstName}. Your token is valid and this hub is now unlocked.
            Navigate directly to Business or Implementation.
          </p>
        </Reveal>

        <div className="orb-stage">
          {portals.map((portal) => (
            <PortalOrb key={portal.id} portal={portal} />
          ))}
        </div>
      </RevealGroup>

      {introVisible && (
        <IntroGreeting
          firstName={firstName}
          onDone={() => setIntroVisible(false)}
        />
      )}
    </main>
  );
}
