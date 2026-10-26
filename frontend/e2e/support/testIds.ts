/**
 * The single source of truth for `data-testid` string literals used by
 * specs. Values must stay byte-identical to the literals added to
 * production markup in `apps/dashboard` and `libs/landing-hub` (architecture
 * §11, the closed list — eleven attributes across six files). Renaming a
 * value here without a matching production-markup change is a two-file
 * change, and grep finds both.
 */

export const testIds = {
  authLoading: 'auth-loading',
  navPlatform: 'nav-platform',
  navWorkflow: 'nav-workflow',
  navSpecFiles: 'nav-spec-files',
  navSkillsHub: 'nav-skills-hub',
  navSpecQueue: 'nav-spec-queue',
  queueState: 'queue-state',
  queueAdd: 'queue-add',
  autoMergeSettingToggle: 'auto-merge-setting-toggle',
  autoMergeSettingDescription: 'auto-merge-setting-description',
  addToQueueSpecError: 'add-to-queue-spec-error',
  addToQueueSubmit: 'add-to-queue-submit',
  addToQueueSubmitReason: 'add-to-queue-submit-reason',
  navHub: 'nav-hub',
  projectCard: 'project-card',
  projectSelectionEmpty: 'project-selection-empty',
  authStatusScreen: 'auth-status-screen',
  landingHero: 'landing-hero',
  introGreeting: 'intro-greeting',
} as const;

/**
 * `libs/landing-hub/src/PortalOrb/PortalOrb.tsx` renders
 * `data-testid={`portal-orb-${portal.id}`}` for each portal orb. The two
 * portal ids are confirmed against the shipped
 * `libs/landing-hub/src/LandingPage/LandingPage.tsx` (`portals` array):
 * `business` and `implementation`.
 */
export const PORTAL_ORB_IDS = {
  business: 'business',
  implementation: 'implementation',
} as const;

export function portalOrbTestId(portalId: string): string {
  return `portal-orb-${portalId}`;
}
