import Reveal from '../../ui/motion/Reveal.jsx';
import FrameOctagon from '../../ui/frames/FrameOctagon.jsx';
import './AuthStatusScreen.scss';

export default function AuthStatusScreen({ status, error }) {
  return (
    <main className="app-shell auth-shell" data-testid="auth-status-screen">
      <div className="ambient ambient-blue" />
      <div className="ambient ambient-purple" />
      <div className="scanlines" />

      <Reveal
        as="section"
        className="auth-card"
        duration={1.1}
        from={{ scale: 0.94, y: 16 }}
      >
        <FrameOctagon
          duration={1.1}
          style={{
            '--frame-line-color': status === 'error' ? 'hsl(0 100% 70%)' : 'hsl(184 100% 58%)',
            '--frame-bg-color': 'hsl(230 50% 12% / 58%)'
          }}
        />
        <div className="auth-card-content">
          <span className="eyebrow">Metafactory Auth</span>
          <h1>{status === 'error' ? 'Authentication failed' : 'Checking authentication'}</h1>
          <p>
            {status === 'error'
              ? 'Keycloak could not be initialized. Check that Docker Compose is running and Keycloak is reachable.'
              : 'You are being redirected to Keycloak. The landing page is only shown with a valid auth token.'}
          </p>
          {error && <pre className="auth-error">{String(error?.message || error)}</pre>}
          {status === 'error' && (
            <button className="nav-button auth-retry" type="button" onClick={() => window.location.reload()}>
              Try again
            </button>
          )}
        </div>
      </Reveal>
    </main>
  );
}
