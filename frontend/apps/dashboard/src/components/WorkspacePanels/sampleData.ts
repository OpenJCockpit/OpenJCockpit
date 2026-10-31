import type { TFunction } from '@openjcockpit/i18n';

/**
 * The server sends the Quality and Evidence sample rows as English text. Known rows are translated
 * here by their text; anything else (a real value, a number) is shown as sent.
 */
const KEYS: Record<string, Record<string, string>> = {
  'quality.controls': {
    'Unit test coverage': 'quality.controls.unitTestCoverage',
    'NIST 2': 'quality.controls.nist2',
    'OWASP ZAP': 'quality.controls.owaspZap',
    'ISO 25010': 'quality.controls.iso25010',
    SAST: 'quality.controls.sast',
    'Dependency scan': 'quality.controls.dependencyScan',
    'Architecture rules': 'quality.controls.architectureRules',
  },
  'quality.details': {
    'No critical issues': 'quality.details.noCriticalIssues',
    '8/8 attributes': 'quality.details.allAttributes',
    'No high issues': 'quality.details.noHighIssues',
    'No known vulnerabilities': 'quality.details.noKnownVulnerabilities',
    '0 deviations': 'quality.details.noDeviations',
  },
  'evidence.events': {
    'Spec loaded': 'evidence.events.specLoaded',
    'Impact analysis recorded': 'evidence.events.impactAnalysisRecorded',
    'Test set generated': 'evidence.events.testSetGenerated',
    'Implementation proposal saved': 'evidence.events.implementationProposalSaved',
    'Human approval logged': 'evidence.events.humanApprovalLogged',
    'Evidence data recorded': 'evidence.events.evidenceDataRecorded',
  },
  'evidence.actors': {
    'Requirement Agent': 'evidence.actors.requirement',
    'Impact Analysis Agent': 'evidence.actors.impact',
    'Test Design Agent': 'evidence.actors.testDesign',
    'Implementation Agent': 'evidence.actors.implementation',
    'Review Agent': 'evidence.actors.review',
    'Evidence Agent': 'evidence.actors.evidence',
  },
};

function sampleLabel(t: TFunction<'dashboard'>, group: string, raw: string) {
  const key = KEYS[group]?.[raw];
  return key ? t(key as never) : raw;
}

export const controlName = (t: TFunction<'dashboard'>, raw: string) =>
  sampleLabel(t, 'quality.controls', raw);
export const controlDetail = (t: TFunction<'dashboard'>, raw: string) =>
  sampleLabel(t, 'quality.details', raw);
export const eventTitle = (t: TFunction<'dashboard'>, raw: string) =>
  sampleLabel(t, 'evidence.events', raw);
export const actorName = (t: TFunction<'dashboard'>, raw: string) =>
  sampleLabel(t, 'evidence.actors', raw);
