import { createRoot } from 'react-dom/client';
import './styles.scss';
import App from './App.jsx';

// StrictMode is intentionally NOT enabled here (ADR-8 gate result).
// The original Arwes-related justification no longer applies now that
// Arwes has been removed from this app, and the new view layer under
// apps/landing/src/ui/ was specifically designed to be StrictMode-safe
// (no render-time side effects; every effect owner tears down fully).
// However, App()'s auth effect and auth/keycloak.js are an explicit
// no-touch zone for this change (BR-4/AC-9), and together they have a
// genuine, verified StrictMode incompatibility that lives entirely outside
// the code this story is allowed to touch: auth/keycloak.js's
// `getKeycloak()` returns a module-level Keycloak singleton, and
// keycloak-js throws "A 'Keycloak' instance can only be initialized once."
// on a second `.init()` call. React 18 StrictMode double-invokes effects
// in development (mount, cleanup, mount again), so the second invocation
// of App()'s effect calls `keycloak.init(...)` on the same
// already-initializing instance a second time; that call rejects with the
// error above and is caught by the existing `.catch()`, which sets
// authState to 'error' — meaning every `npm run dev` load would
// incorrectly show the "Authentication failed" screen even when Keycloak is
// perfectly reachable. Reproduced directly against `keycloak-js` during
// implementation (not assumed): calling `.init()` twice on one instance
// yields exactly `Error("A 'Keycloak' instance can only be initialized
// once.")` on the second call. This does not affect the production build
// (React does not double-invoke effects outside development), but it does
// break local development for every contributor, and fixing it would
// require editing the protected `App()` effect or the singleton in
// auth/keycloak.js, neither of which this change is authorized to touch.
// Enabling StrictMode here is therefore a deliberate, separate follow-up
// that must ship together with a fix to the singleton/effect pattern (e.g.
// an idempotent init guard), not silently bundled into this migration.
createRoot(document.getElementById('root')).render(<App />);
