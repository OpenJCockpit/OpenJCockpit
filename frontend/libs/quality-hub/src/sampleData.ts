import type { TFunction } from '@openjcockpit/i18n';

/**
 * The server sends the sample rows as English text. Known rows are translated here by their text;
 * anything else (a real value, a number) is shown as sent.
 */
const CONTROLS: Record<string, string> = {
  'Unit test coverage': 'controls.unitTestCoverage',
  'NIST 2': 'controls.nist2',
  'OWASP ZAP': 'controls.owaspZap',
  'ISO 25010': 'controls.iso25010',
  SAST: 'controls.sast',
  'Dependency scan': 'controls.dependencyScan',
  'Architecture rules': 'controls.architectureRules',
};

const DETAILS: Record<string, string> = {
  'No critical issues': 'details.noCriticalIssues',
  '8/8 attributes': 'details.allAttributes',
  'No high issues': 'details.noHighIssues',
  'No known vulnerabilities': 'details.noKnownVulnerabilities',
  '0 deviations': 'details.noDeviations',
};

function translate(t: TFunction<'quality'>, keys: Record<string, string>, raw: string) {
  return raw in keys ? t(keys[raw] as never) : raw;
}

export const controlName = (t: TFunction<'quality'>, raw: string) => translate(t, CONTROLS, raw);
export const controlDetail = (t: TFunction<'quality'>, raw: string) => translate(t, DETAILS, raw);
