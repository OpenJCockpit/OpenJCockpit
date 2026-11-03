import type { TFunction } from '@openjcockpit/i18n';

/**
 * The server sends the sample rows as English text. Known rows are translated here by their text;
 * anything else (a real value, a number) is shown as sent.
 */
const EVENTS: Record<string, string> = {
  'Spec loaded': 'events.specLoaded',
  'Impact analysis recorded': 'events.impactAnalysisRecorded',
  'Test set generated': 'events.testSetGenerated',
  'Implementation proposal saved': 'events.implementationProposalSaved',
  'Human approval logged': 'events.humanApprovalLogged',
  'Evidence data recorded': 'events.evidenceDataRecorded',
};

const ACTORS: Record<string, string> = {
  'Requirement Agent': 'actors.requirement',
  'Impact Analysis Agent': 'actors.impact',
  'Test Design Agent': 'actors.testDesign',
  'Implementation Agent': 'actors.implementation',
  'Review Agent': 'actors.review',
  'Evidence Agent': 'actors.evidence',
};

function translate(t: TFunction<'evidence'>, keys: Record<string, string>, raw: string) {
  return raw in keys ? t(keys[raw] as never) : raw;
}

export const eventTitle = (t: TFunction<'evidence'>, raw: string) => translate(t, EVENTS, raw);
export const actorName = (t: TFunction<'evidence'>, raw: string) => translate(t, ACTORS, raw);
