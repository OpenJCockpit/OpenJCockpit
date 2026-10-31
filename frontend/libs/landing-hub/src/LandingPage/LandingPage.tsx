import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from '@openjcockpit/i18n';
import { PuffsBackground, Reveal, RevealGroup } from '@openjcockpit/ui';
import { AmbientBackdrop } from '../AmbientBackdrop/AmbientBackdrop';
import { AppShell } from '../AppShell/AppShell';
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

type LandingPageProps = {
  user: HubUser;
  onSelectPortal: (portal: PortalId) => void;
  onLogout: () => void;
};

export function LandingPage({ user, onSelectPortal, onLogout }: LandingPageProps) {
  const { t } = useTranslation('landing');
  const portals: Portal[] = [
    {
      id: 'business',
      title: t('portals.business.title'),
      subtitle: t('portals.business.subtitle'),
      side: 'left',
    },
    {
      id: 'implementation',
      title: t('portals.implementation.title'),
      subtitle: t('portals.implementation.subtitle'),
      side: 'right',
    },
  ];
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
          <h2 id="landing-title">{t('hero.title')}</h2>
          <p>{t('hero.welcome', { firstName })}</p>
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
