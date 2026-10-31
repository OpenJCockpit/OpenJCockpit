import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { renderWithI18n, renderWithPseudoI18n, untranslatedText } from '@openjcockpit/i18n/testing';
import type { EvidenceEvent, QualityControl } from '@openjcockpit/api';
import { dashboardResources } from '../../i18n';
import { EvidencePanel } from './EvidencePanel';
import { QualityPanel } from './QualityPanel';

// The server sends these rows as English text.
const CONTROLS: QualityControl[] = [
  { name: 'Unit test coverage', status: 'Compliant', statusText: '92%' },
  { name: 'OWASP ZAP', status: 'Compliant', statusText: 'No critical issues' },
  { name: 'Dependency scan', status: 'Compliant', statusText: 'No known vulnerabilities' },
];
const EVENTS: EvidenceEvent[] = [
  {
    time: '13:16',
    title: 'Implementation proposal saved',
    actor: 'Implementation Agent',
    status: 'ok',
  },
  { time: '13:22', title: 'Human approval logged', actor: 'Review Agent', status: 'ok' },
  { time: '13:24', title: 'Evidence data recorded', actor: 'Evidence Agent', status: 'ok' },
];

describe('QualityPanel', () => {
  it('shows the rows as sent in English', () => {
    renderWithI18n(<QualityPanel controls={CONTROLS} />, { resources: dashboardResources });
    expect(screen.getByText('Unit test coverage')).toBeInTheDocument();
    expect(screen.getByText('No critical issues')).toBeInTheDocument();
    expect(screen.getByText('92%')).toBeInTheDocument();
  });

  it('translates every known row, leaving only values such as 92% as sent', () => {
    renderWithPseudoI18n(<QualityPanel controls={CONTROLS} />, { resources: dashboardResources });
    expect(untranslatedText(document.body)).toEqual([]);
  });

  it('shows a row it does not know as sent', () => {
    renderWithPseudoI18n(
      <QualityPanel controls={[{ name: 'Custom gate', status: 'Compliant', statusText: '' }]} />,
      { resources: dashboardResources },
    );
    expect(untranslatedText(document.body)).toEqual(['Custom gate']);
  });
});

describe('EvidencePanel', () => {
  it('translates every known event and agent name', () => {
    renderWithPseudoI18n(<EvidencePanel events={EVENTS} />, { resources: dashboardResources });
    expect(untranslatedText(document.body, [/^\d{2}:\d{2}$/])).toEqual([]);
  });
});
