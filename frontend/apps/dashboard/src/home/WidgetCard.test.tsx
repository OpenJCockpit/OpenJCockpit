import { describe, expect, it } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router';
import { withI18n } from '@openjcockpit/i18n/testing';
import type { WidgetDefinition } from '@openjcockpit/hub-contract';
import { appResources } from '../i18nResources';
import { clickAsync } from '../testUtils/dom';
import { WidgetCard } from './WidgetCard';

const widget: WidgetDefinition = {
  id: 'payments',
  titleKey: 'Payments',
  sourceAppId: 'demo',
  requiredPermissions: [],
  component: () => Promise.resolve({ default: () => <p>payments body</p> }),
  defaultFor: [],
  link: '/payments',
};

function Where() {
  return <p data-testid="where">{useLocation().pathname}</p>;
}

async function renderCard(item = { id: 'payments', cols: 2, rows: 1 }) {
  await act(async () => {
    render(
      withI18n(
        <MemoryRouter>
          <WidgetCard widget={widget} item={item} />
          <Where />
        </MemoryRouter>,
        appResources,
      ),
    );
  });
}

describe('WidgetCard', () => {
  it('shows the title and the widget body', async () => {
    await renderCard();
    expect(screen.getByRole('heading', { name: 'Payments' })).toBeInTheDocument();
    expect(await screen.findByText('payments body')).toBeInTheDocument();
  });

  it('exposes its grid size', async () => {
    await renderCard({ id: 'payments', cols: 3, rows: 2 });
    const card = screen.getByTestId('widget-payments');
    expect(card).toHaveAttribute('data-cols', '3');
    expect(card).toHaveAttribute('data-rows', '2');
  });

  it('is not draggable and offers a "View <title>" link to the widget page', async () => {
    await renderCard();
    expect(screen.getByTestId('widget-payments')).not.toHaveAttribute('draggable', 'true');
    const link = screen.getByTestId('widget-open-payments');
    expect(link).toHaveTextContent('View Payments');
    await clickAsync(link);
    expect(screen.getByTestId('where')).toHaveTextContent('/payments');
  });
});
