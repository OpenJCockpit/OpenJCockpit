/**
 * The single source of truth for `data-testid` string literals used by
 * specs. Values must stay byte-identical to the literals added to
 * production markup in `apps/dashboard` and `apps/landing` (architecture
 * §11, the closed list — eleven attributes across six files). Renaming a
 * value here without a matching production-markup change is a two-file
 * change, and grep finds both.
 */

export const testIds = {
  authLoading: 'auth-loading',
  navWorkflow: 'nav-workflow',
  navSpecFiles: 'nav-spec-files',
  navSkillsHub: 'nav-skills-hub',
  navPortal: 'nav-portal',
  projectCard: 'project-card',
  projectSelectionEmpty: 'project-selection-empty',
  authStatusScreen: 'auth-status-screen',
  landingHero: 'landing-hero',
  introGreeting: 'intro-greeting',
} as const;

/**
 * `apps/landing/src/components/PortalOrb/PortalOrb.jsx` renders
 * `data-testid={`portal-orb-${portal.id}`}` for each portal orb. The two
 * portal ids are confirmed against the shipped
 * `apps/landing/src/screens/LandingPage/LandingPage.jsx` (`portals` array):
 * `business` and `implementation`.
 */
export const PORTAL_ORB_IDS = {
  business: 'business',
  implementation: 'implementation',
} as const;

export function portalOrbTestId(portalId: string): string {
  return `portal-orb-${portalId}`;
}
