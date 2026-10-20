import { useEffect, useMemo, useState } from 'react';
import { PuffsBackground, Reveal, RevealGroup } from '@openjcockpit/ui';
import { AmbientBackdrop } from '../AmbientBackdrop/AmbientBackdrop';
import { AppShell } from '../AppShell/AppShell';
import { Eyebrow } from '../Eyebrow/Eyebrow';
import { IntroGreeting } from '../IntroGreeting/IntroGreeting';
import { PortalOrb } from '../PortalOrb/PortalOrb';
import type { Portal, PortalId } from '../PortalOrb/PortalOrb';
import { TopBar } from '../TopBar/TopBar';
import type { HubUser } from '../types';
import styles from './LandingPage.module.scss';

const INTRO_SEEN_KEY = 'openjcockpit_hub_intro_seen';

function wasIntroSeen(): boolean {
  try {
    return sessionStorage.getItem(INTRO_SEEN_KEY) === '1';
  } catch {
    return false;
  }
}

function markIntroSeen() {
  try {
    sessionStorage.setItem(INTRO_SEEN_KEY, '1');
  } catch {
    // Storage unavailable: the intro simply replays next time.
  }
}

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

type LandingPageProps = {
  user: HubUser;
  onSelectPortal: (portal: PortalId) => void;
  onLogout: () => void;
};

export function LandingPage({ user, onSelectPortal, onLogout }: LandingPageProps) {
  const [introVisible, setIntroVisible] = useState(() => !wasIntroSeen());

  useEffect(markIntroSeen, []);

  const firstName = useMemo(() => user?.firstName || 'Tony', [user]);

  return (
    <AppShell>
      <PuffsBackground
        color="hsl(184 100% 58% / 0.26)"
        quantity={85}
        padding={0}
        xOffset={[0, 80]}
        yOffset={[-10, -90]}
        radiusOffset={[1, 9]}
      />

      <AmbientBackdrop />

      <TopBar user={user} onLogout={onLogout} />

      <RevealGroup
        as="section"
        className={styles.hero}
        aria-labelledby="landing-title"
        stagger={0.16}
        data-testid="landing-hero"
      >
        <Reveal as="div" className={styles.heroCopy} duration={0.55} index={0} from={{ y: 24 }}>
          <Eyebrow>Authenticated destination</Eyebrow>
          <h2 id="landing-title">Choose your workspace</h2>
          <p>
            Welcome back, {firstName}. Your token is valid and this hub is now unlocked. Navigate
            directly to Business or Implementation.
          </p>
        </Reveal>

        <div className={styles.orbStage}>
          {portals.map((portal) => (
            <PortalOrb key={portal.id} portal={portal} onSelect={onSelectPortal} />
          ))}
        </div>
      </RevealGroup>

      {introVisible && (
        <IntroGreeting firstName={firstName} onDone={() => setIntroVisible(false)} />
      )}
    </AppShell>
  );
}
