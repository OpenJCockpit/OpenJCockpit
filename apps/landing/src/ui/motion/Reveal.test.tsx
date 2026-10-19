import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import Reveal from './Reveal';

describe('Reveal', () => {
  it('renders the requested element and passes props through', () => {
    render(
      <Reveal as="button" type="button" data-testid="orb">
        go
      </Reveal>,
    );

    const el = screen.getByTestId('orb');
    expect(el.tagName).toBe('BUTTON');
    expect(el).toHaveAttribute('type', 'button');
    expect(el).toHaveTextContent('go');
  });

  it('exposes duration, index and from as custom properties', () => {
    render(
      <Reveal data-testid="r" duration={1.5} index={2} from={{ y: 24, scale: 0.9 }}>
        x
      </Reveal>,
    );

    const style = screen.getByTestId('r').style;
    expect(style.getPropertyValue('--reveal-duration')).toBe('1.5s');
    expect(style.getPropertyValue('--reveal-index')).toBe('2');
    expect(style.getPropertyValue('--reveal-y-from')).toBe('24px');
    expect(style.getPropertyValue('--reveal-scale-from')).toBe('0.9');
  });

  it('omits from-properties that are not given', () => {
    render(<Reveal data-testid="r">x</Reveal>);
    expect(screen.getByTestId('r').style.getPropertyValue('--reveal-y-from')).toBe('');
  });
});
