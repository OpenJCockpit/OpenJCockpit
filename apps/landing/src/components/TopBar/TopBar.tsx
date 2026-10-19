import { useState } from 'react';
import type Keycloak from 'keycloak-js';
import type { User } from '../../auth/keycloak';
import Reveal from '../../ui/motion/Reveal';
import FrameOctagon from '../../ui/frames/FrameOctagon';
import NavButton from '../../ui/NavButton/NavButton';
import styles from './TopBar.module.scss';

const navActions = [
  { label: 'Profile', icon: '◉', type: 'profile' },
  { label: 'Notifications', icon: '◇', type: 'notifications' },
  { label: 'Messages', icon: '✦', type: 'messages' },
];

type TopBarProps = { user: User; keycloak: Keycloak };

export default function TopBar({ user, keycloak }: TopBarProps) {
  const [activePanel, setActivePanel] = useState<string | null>(null);

  return (
    <Reveal as="header" className={styles.topbar} duration={0.8} from={{ y: -20 }}>
      <FrameOctagon className={styles.frame} duration={0.8} />
      <div className={styles.brand}>
        <span className={styles.brandMark}>A</span>
        <div>
          <strong>NEXUS PORTAL</strong>
          <small>{user?.email || user?.username || 'Authenticated launch hub'}</small>
        </div>
      </div>
      <nav className={styles.topbarActions} aria-label="User actions">
        {navActions.map((action) => (
          <NavButton
            key={action.label}
            type="button"
            onClick={() => setActivePanel(activePanel === action.type ? null : action.type)}
          >
            <span aria-hidden="true">{action.icon}</span>
            <span>{action.label}</span>
          </NavButton>
        ))}
        <NavButton
          className={styles.logoutButton}
          type="button"
          onClick={() => void keycloak.logout({ redirectUri: window.location.origin })}
        >
          Logout
        </NavButton>
      </nav>
      {activePanel && (
        <div className={styles.topbarPopover}>
          <strong>{activePanel}</strong>
          {activePanel === 'profile' && (
            <p>
              Logged in as {user.firstName} ({user.username}). Token valid until session refresh or
              logout.
            </p>
          )}
          {activePanel === 'notifications' && <p>No new notifications.</p>}
          {activePanel === 'messages' && <p>No new messages.</p>}
        </div>
      )}
    </Reveal>
  );
}
