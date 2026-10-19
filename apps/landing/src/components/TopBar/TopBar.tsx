import { useState } from 'react';
import type Keycloak from 'keycloak-js';
import type { User } from '../../auth/keycloak';
import Reveal from '../../ui/motion/Reveal';
import FrameOctagon from '../../ui/frames/FrameOctagon';
import './TopBar.scss';

const navActions = [
  { label: 'Profile', icon: '◉', type: 'profile' },
  { label: 'Notifications', icon: '◇', type: 'notifications' },
  { label: 'Messages', icon: '✦', type: 'messages' },
];

type TopBarProps = { user: User; keycloak: Keycloak };

export default function TopBar({ user, keycloak }: TopBarProps) {
  const [activePanel, setActivePanel] = useState<string | null>(null);

  return (
    <Reveal as="header" className="topbar" duration={0.8} from={{ y: -20 }}>
      <FrameOctagon
        duration={0.8}
        // eslint-disable-next-line no-restricted-syntax -- static custom properties; move into the CSS module in the CSS Modules PR
        style={{
          '--frame-line-color': 'hsl(184 100% 58% / 78%)',
          '--frame-bg-color': 'hsl(230 50% 12% / 46%)',
        }}
      />

      <div className="brand">
        <span className="brand-mark">A</span>
        <div>
          <strong>NEXUS PORTAL</strong>
          <small>{user?.email || user?.username || 'Authenticated launch hub'}</small>
        </div>
      </div>

      <nav className="topbar-actions" aria-label="User actions">
        {navActions.map((action) => (
          <button
            key={action.label}
            className="nav-button"
            type="button"
            onClick={() => setActivePanel(activePanel === action.type ? null : action.type)}
          >
            <span aria-hidden="true">{action.icon}</span>
            <span>{action.label}</span>
          </button>
        ))}
        <button
          className="nav-button logout-button"
          type="button"
          onClick={() => void keycloak.logout({ redirectUri: window.location.origin })}
        >
          Logout
        </button>
      </nav>

      {activePanel && (
        <div className="topbar-popover">
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
