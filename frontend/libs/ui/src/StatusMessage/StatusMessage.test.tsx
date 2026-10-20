import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { fieldErrorClass, fieldHintClass } from '../Form/Form';
import { StatusMessage } from './StatusMessage';

describe('StatusMessage', () => {
  it('renders info as a hint by default', () => {
    render(<StatusMessage>Saved</StatusMessage>);
    expect(screen.getByText('Saved').className).toBe(fieldHintClass);
  });

  it('renders error as a field error and forwards attributes', () => {
    render(
      <StatusMessage variant="error" role="alert" className="extra">
        Boom
      </StatusMessage>,
    );
    const el = screen.getByRole('alert');
    expect(el.className).toBe(`${fieldErrorClass} extra`);
  });

  it('renders loading as a block element and forwards attributes', () => {
    render(
      <StatusMessage variant="loading" data-testid="loading">
        Loading things...
      </StatusMessage>,
    );
    const el = screen.getByTestId('loading');
    expect(el.tagName).toBe('DIV');
    expect(el).toHaveTextContent('Loading things...');
  });
});
