import Reveal from '../../ui/motion/Reveal';
import FrameOctagon from '../../ui/frames/FrameOctagon';
import AppShell from '../../ui/AppShell/AppShell';
import AmbientBackdrop from '../../ui/AmbientBackdrop/AmbientBackdrop';
import Eyebrow from '../../ui/Eyebrow/Eyebrow';
import NavButton from '../../ui/NavButton/NavButton';
import { cx } from '../../ui/cx';
import styles from './AuthStatusScreen.module.scss';

type AuthStatusScreenProps = { status: 'initializing' | 'error'; error?: unknown };

function errorMessage(error: unknown): string {
  return String((error as { message?: string } | null)?.message || error);
}

export default function AuthStatusScreen({ status, error }: AuthStatusScreenProps) {
  return (
    <AppShell className={styles.authShell} data-testid="auth-status-screen">
      <AmbientBackdrop />
      <Reveal as="section" className={styles.authCard} duration={1.1} from={{ scale: 0.94, y: 16 }}>
        <FrameOctagon
          duration={1.1}
          className={cx(styles.authFrame, status === 'error' && styles.authFrameError)}
        />
        <div className={styles.authCardContent}>
          <Eyebrow>OpenJCockpit Auth</Eyebrow>
          <h1>{status === 'error' ? 'Authentication failed' : 'Checking authentication'}</h1>
          <p>
            {status === 'error'
              ? 'Keycloak could not be initialized. Check that Docker Compose is running and Keycloak is reachable.'
              : 'You are being redirected to Keycloak. The landing page is only shown with a valid auth token.'}
          </p>
          {error ? <pre className={styles.authError}>{errorMessage(error)}</pre> : null}
          {status === 'error' && (
            <NavButton
              className={styles.authRetry}
              type="button"
              onClick={() => window.location.reload()}
            >
              Try again
            </NavButton>
          )}
        </div>
      </Reveal>
    </AppShell>
  );
}
