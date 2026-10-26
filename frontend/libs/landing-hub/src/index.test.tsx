import { render, screen } from '@testing-library/react';
import { Suspense } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { LandingHub } from './index';

// jsdom has no canvas; the particle background is not under test here.
vi.mock('@openjcockpit/ui', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@openjcockpit/ui')>()),
  PuffsBackground: () => null,
  DecipherText: ({ text }: { text: string }) => <h1>{text}</h1>,
}));

describe('LandingHub', () => {
  it('lazy-loads the landing page', async () => {
    sessionStorage.clear();
    render(
      <Suspense fallback={<p>loading</p>}>
        <LandingHub
          user={{ username: 'tstark', firstName: 'Tony', email: undefined }}
          onSelectPortal={vi.fn()}
          onLogout={vi.fn()}
        />
      </Suspense>,
    );

    expect(await screen.findByTestId('landing-hero')).toBeInTheDocument();
  });
});
