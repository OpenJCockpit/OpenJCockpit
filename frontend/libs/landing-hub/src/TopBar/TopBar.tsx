import { useState } from 'react';
import { useTranslation } from '@openjcockpit/i18n';
import { FrameOctagon, Reveal } from '@openjcockpit/ui';
import { NavButton } from '../NavButton/NavButton';
import type { HubUser } from '../types';
import styles from './TopBar.module.scss';

const navActions = [
  { icon: '◉', type: 'profile' },
  { icon: '◇', type: 'notifications' },
  { icon: '✦', type: 'messages' },
] as const;

type TopBarProps = { user: HubUser; onLogout: () => void };

export function TopBar({ user, onLogout }: TopBarProps) {
  const { t } = useTranslation('landing');
  const [activePanel, setActivePanel] = useState<(typeof navActions)[number]['type'] | null>(null);

  return (
    <Reveal as="header" className={styles.topbar} duration={0.8} from={{ y: -20 }}>
      <FrameOctagon className={styles.frame} duration={0.8} />
      <div className={styles.brand}>
        <span className={styles.brandMark}>A</span>
        <div>
          <strong>{t('topBar.brand')}</strong>
          <small>{user?.email || user?.username || t('topBar.fallbackIdentity')}</small>
        </div>
      </div>
      <nav className={styles.topbarActions} aria-label={t('topBar.userActions')}>
        {navActions.map((action) => (
          <NavButton
            key={action.type}
            type="button"
            onClick={() => setActivePanel(activePanel === action.type ? null : action.type)}
          >
            <span aria-hidden="true">{action.icon}</span>
            <span>{t(`topBar.actions.${action.type}`)}</span>
          </NavButton>
        ))}
        <NavButton className={styles.logoutButton} type="button" onClick={onLogout}>
          {t('topBar.logout')}
        </NavButton>
      </nav>
      {activePanel && (
        <div className={styles.topbarPopover}>
          <strong>{t(`topBar.panels.${activePanel}`)}</strong>
          {activePanel === 'profile' && (
            <p>
              {t('topBar.profileSummary', { firstName: user.firstName, username: user.username })}
            </p>
          )}
          {activePanel === 'notifications' && <p>{t('topBar.noNotifications')}</p>}
          {activePanel === 'messages' && <p>{t('topBar.noMessages')}</p>}
        </div>
      )}
    </Reveal>
  );
}
