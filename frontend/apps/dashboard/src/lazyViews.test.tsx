import { render, screen } from '@testing-library/react';
import { Suspense, type ComponentType } from 'react';
import { describe, expect, it, vi } from 'vitest';
import * as lazyViews from './lazyViews';

// Each feature lib is replaced by plain stand-ins so the real lazy factories resolve cheaply.
const stub = vi.hoisted(() => (name: string) => () => <p data-testid="resolved">{name}</p>);
vi.mock('@openjcockpit/project', () => ({
  ProjectSelection: stub('ProjectSelection'),
}));
vi.mock('@openjcockpit/spec-files', () => ({ SpecFilesDashboard: stub('SpecFilesDashboard') }));
vi.mock('@openjcockpit/spec-queue', () => ({
  SpecQueueDashboard: stub('SpecQueueDashboard'),
  AddToQueueDialog: stub('AddToQueueDialog'),
}));
vi.mock('@openjcockpit/workflow', () => ({
  WorkflowDashboard: stub('WorkflowDashboard'),
  WorkflowPromptDialog: stub('WorkflowPromptDialog'),
}));

describe('lazyViews', () => {
  it.each(Object.keys(lazyViews))('resolves %s from its feature library', async (name) => {
    const View = lazyViews[name as keyof typeof lazyViews] as unknown as ComponentType;
    render(
      <Suspense fallback={null}>
        <View />
      </Suspense>,
    );
    expect(await screen.findByTestId('resolved')).toHaveTextContent(name);
  });
});
