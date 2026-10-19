import { act, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import IntroGreeting from './IntroGreeting';

vi.mock('../../ui/vendor/react-bits/DecipherText', () => ({
  default: ({ text }: { text: string }) => <h1>{text}</h1>,
}));

beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());

describe('IntroGreeting', () => {
  it('greets the user', () => {
    render(<IntroGreeting firstName="Tony" onDone={() => undefined} />);

    expect(screen.getByTestId('intro-greeting')).toHaveAttribute('aria-label', 'Hi Tony!');
    expect(screen.getByRole('heading', { name: 'Hi Tony!' })).toBeInTheDocument();
  });

  it('dismisses itself after the intro duration', () => {
    const onDone = vi.fn();
    render(<IntroGreeting firstName="Tony" onDone={onDone} />);

    act(() => void vi.advanceTimersByTime(2849));
    expect(onDone).not.toHaveBeenCalled();

    act(() => void vi.advanceTimersByTime(1));
    expect(onDone).toHaveBeenCalledTimes(1);
  });

  it('cancels the timer on unmount', () => {
    const onDone = vi.fn();
    const { unmount } = render(<IntroGreeting firstName="Tony" onDone={onDone} />);

    unmount();
    act(() => void vi.advanceTimersByTime(5000));

    expect(onDone).not.toHaveBeenCalled();
  });
});
