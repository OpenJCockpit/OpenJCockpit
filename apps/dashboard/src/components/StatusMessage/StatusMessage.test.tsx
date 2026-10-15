import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { StatusMessage } from './StatusMessage';

describe('StatusMessage', () => {
  it('renders info as settings-desc by default', () => {
    render(<StatusMessage>Saved</StatusMessage>);
    expect(screen.getByText('Saved').className).toBe('settings-desc');
  });

  it('renders error as settings-error and forwards attributes', () => {
    render(
      <StatusMessage variant="error" role="alert" className="extra">
        Boom
      </StatusMessage>,
    );
    const el = screen.getByRole('alert');
    expect(el.className).toBe('settings-error extra');
  });
});
