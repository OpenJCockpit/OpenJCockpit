import { createRef } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Tooltip, positionTooltip } from './Tooltip';

function rect(overrides: Partial<DOMRect>): DOMRect {
  return {
    top: 0,
    bottom: 0,
    left: 0,
    right: 0,
    width: 0,
    height: 0,
    x: 0,
    y: 0,
    toJSON() {
      return this;
    },
    ...overrides,
  };
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('positionTooltip', () => {
  it('centers horizontally over the anchor', () => {
    const result = positionTooltip(
      { top: 300, bottom: 340, left: 200, width: 100 },
      { width: 160, height: 40 },
      'top',
      { width: 2000, height: 2000 },
    );
    expect(result.left).toBe(170);
  });

  it('clamps left to MARGIN near the left edge', () => {
    const result = positionTooltip(
      { top: 300, bottom: 320, left: 0, width: 20 },
      { width: 160, height: 40 },
      'top',
      { width: 2000, height: 2000 },
    );
    expect(result.left).toBe(8);
  });

  it('clamps right to viewport.width - tip.width - MARGIN near the right edge', () => {
    const result = positionTooltip(
      { top: 300, bottom: 320, left: 1990, width: 20 },
      { width: 160, height: 40 },
      'top',
      { width: 2000, height: 2000 },
    );
    expect(result.left).toBe(2000 - 160 - 8);
  });

  it('places above the anchor with room, placement stays top', () => {
    const result = positionTooltip(
      { top: 300, bottom: 340, left: 200, width: 100 },
      { width: 160, height: 40 },
      'top',
      { width: 2000, height: 2000 },
    );
    expect(result.top).toBe(300 - 10 - 40);
    expect(result.placement).toBe('top');
  });

  it('flips top to bottom when there is no room above', () => {
    const result = positionTooltip(
      { top: 20, bottom: 60, left: 200, width: 100 },
      { width: 160, height: 40 },
      'top',
      { width: 2000, height: 2000 },
    );
    expect(result.placement).toBe('bottom');
    expect(result.top).toBe(60 + 10);
  });

  it('flips bottom to top when there is no room below', () => {
    const result = positionTooltip(
      { top: 300, bottom: 1990, left: 200, width: 100 },
      { width: 160, height: 40 },
      'bottom',
      { width: 2000, height: 2000 },
    );
    expect(result.placement).toBe('top');
    expect(result.top).toBe(300 - 10 - 40);
  });
});

describe('Tooltip', () => {
  it('is a passthrough that renders the trigger with no tooltip in the document', () => {
    render(
      <Tooltip content="x">
        <button>Go</button>
      </Tooltip>,
    );
    expect(screen.getByRole('button', { name: 'Go' })).toBeInTheDocument();
    expect(screen.queryByRole('tooltip')).toBeNull();
  });

  it('never opens a tooltip when content is null', () => {
    render(
      <Tooltip content={null}>
        <button>Go</button>
      </Tooltip>,
    );
    fireEvent.mouseEnter(screen.getByRole('button', { name: 'Go' }));
    expect(screen.queryByRole('tooltip')).toBeNull();
  });

  it('mouseEnter portals a tooltip onto document.body containing the content, and links aria-describedby', () => {
    const { container } = render(
      <div>
        <Tooltip content="Tip text">
          <button>Go</button>
        </Tooltip>
      </div>,
    );
    const trigger = screen.getByRole('button', { name: 'Go' });
    fireEvent.mouseEnter(trigger);

    const tooltip = screen.getByRole('tooltip');
    expect(tooltip.parentElement).toBe(document.body);
    expect(container.contains(tooltip)).toBe(false);
    expect(tooltip).toHaveTextContent('Tip text');
    expect(trigger.getAttribute('aria-describedby')).toBe(tooltip.id);
  });

  it('mouseLeave removes the tooltip and the aria-describedby link', () => {
    render(
      <Tooltip content="Tip text">
        <button>Go</button>
      </Tooltip>,
    );
    const trigger = screen.getByRole('button', { name: 'Go' });
    fireEvent.mouseEnter(trigger);
    expect(screen.getByRole('tooltip')).toBeInTheDocument();

    fireEvent.mouseLeave(trigger);
    expect(screen.queryByRole('tooltip')).toBeNull();
    expect(trigger.hasAttribute('aria-describedby')).toBe(false);
  });

  it('focus shows the tooltip, blur hides it', () => {
    render(
      <Tooltip content="Tip text">
        <button>Go</button>
      </Tooltip>,
    );
    const trigger = screen.getByRole('button', { name: 'Go' });
    fireEvent.focus(trigger);
    expect(screen.getByRole('tooltip')).toBeInTheDocument();
    fireEvent.blur(trigger);
    expect(screen.queryByRole('tooltip')).toBeNull();
  });

  it('dragStart on the trigger hides an open tooltip', () => {
    render(
      <Tooltip content="Tip text">
        <button draggable>Go</button>
      </Tooltip>,
    );
    const trigger = screen.getByRole('button', { name: 'Go' });
    fireEvent.mouseEnter(trigger);
    expect(screen.getByRole('tooltip')).toBeInTheDocument();
    fireEvent.dragStart(trigger);
    expect(screen.queryByRole('tooltip')).toBeNull();
  });

  it('composes with a child that already has its own onMouseEnter and ref (AD-3 tripwire)', () => {
    const ref = createRef<HTMLButtonElement>();
    const handleEnter = vi.fn();
    render(
      <Tooltip content="Tip text">
        <button ref={ref} onMouseEnter={handleEnter}>
          Go
        </button>
      </Tooltip>,
    );
    const trigger = screen.getByRole('button', { name: 'Go' });
    fireEvent.mouseEnter(trigger);

    expect(handleEnter).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('tooltip')).toBeInTheDocument();
    // A silently no-op ref merge (reading `child.props.ref` instead of the
    // element's own `ref`) would leave this null on react@18.3.1.
    expect(ref.current).toBe(trigger);
  });

  it('renders exactly one tooltip at a time across rapid enter/leave', () => {
    render(
      <Tooltip content="Tip text">
        <button>Go</button>
      </Tooltip>,
    );
    const trigger = screen.getByRole('button', { name: 'Go' });
    fireEvent.mouseEnter(trigger);
    fireEvent.mouseLeave(trigger);
    fireEvent.mouseEnter(trigger);
    expect(screen.getAllByRole('tooltip')).toHaveLength(1);
  });

  it('applies the position computed by positionTooltip', () => {
    const anchorRect = rect({ top: 300, bottom: 340, left: 200, width: 100 });
    const tipRect = rect({ width: 160, height: 40 });
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (
      this: HTMLElement,
    ) {
      return this.getAttribute('role') === 'tooltip' ? tipRect : anchorRect;
    });

    render(
      <Tooltip content="Tip text">
        <button>Go</button>
      </Tooltip>,
    );
    fireEvent.mouseEnter(screen.getByRole('button', { name: 'Go' }));
    const tooltip = screen.getByRole('tooltip');
    const expected = positionTooltip(anchorRect, tipRect, 'top', {
      width: window.innerWidth,
      height: window.innerHeight,
    });

    expect(tooltip.style.top).toBe(`${expected.top}px`);
    expect(tooltip.style.left).toBe(`${expected.left}px`);
  });

  it('repositions on scroll (capture-phase listener)', () => {
    let anchorRect = rect({ top: 300, bottom: 340, left: 200, width: 100 });
    const tipRect = rect({ width: 160, height: 40 });
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (
      this: HTMLElement,
    ) {
      return this.getAttribute('role') === 'tooltip' ? tipRect : anchorRect;
    });

    render(
      <Tooltip content="Tip text">
        <button>Go</button>
      </Tooltip>,
    );
    fireEvent.mouseEnter(screen.getByRole('button', { name: 'Go' }));
    const tooltip = screen.getByRole('tooltip');

    anchorRect = rect({ top: 100, bottom: 140, left: 500, width: 100 });
    fireEvent.scroll(window);

    const expected = positionTooltip(anchorRect, tipRect, 'top', {
      width: window.innerWidth,
      height: window.innerHeight,
    });
    expect(tooltip.style.top).toBe(`${expected.top}px`);
    expect(tooltip.style.left).toBe(`${expected.left}px`);
  });

  it('registers and cleans up scroll/resize listeners with identical options (AD-4 tripwire)', () => {
    const addSpy = vi.spyOn(window, 'addEventListener');
    const removeSpy = vi.spyOn(window, 'removeEventListener');

    const { unmount } = render(
      <Tooltip content="Tip text">
        <button>Go</button>
      </Tooltip>,
    );
    const trigger = screen.getByRole('button', { name: 'Go' });

    fireEvent.mouseEnter(trigger);
    const scrollAdd = addSpy.mock.calls.find(([type]) => type === 'scroll');
    const resizeAdd = addSpy.mock.calls.find(([type]) => type === 'resize');
    expect(scrollAdd?.[2]).toEqual({ capture: true, passive: true });
    expect(resizeAdd?.[2]).toEqual({ capture: true, passive: true });

    fireEvent.mouseLeave(trigger);
    const scrollRemove = removeSpy.mock.calls.find(([type]) => type === 'scroll');
    const resizeRemove = removeSpy.mock.calls.find(([type]) => type === 'resize');
    expect(scrollRemove?.[2]).toEqual({ capture: true, passive: true });
    expect(resizeRemove?.[2]).toEqual({ capture: true, passive: true });

    addSpy.mockClear();
    removeSpy.mockClear();
    fireEvent.mouseEnter(trigger);
    unmount();
    const scrollRemoveOnUnmount = removeSpy.mock.calls.find(([type]) => type === 'scroll');
    const resizeRemoveOnUnmount = removeSpy.mock.calls.find(([type]) => type === 'resize');
    expect(scrollRemoveOnUnmount?.[2]).toEqual({ capture: true, passive: true });
    expect(resizeRemoveOnUnmount?.[2]).toEqual({ capture: true, passive: true });
  });
});
