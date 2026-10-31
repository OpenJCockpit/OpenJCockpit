import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { renderWithI18n } from '@openjcockpit/i18n/testing';
import {
  Actions,
  AppHeader,
  AppShell,
  Backdrop,
  BrandLogo,
  DataTable,
  DataTableRow,
  Eyebrow,
  IconBox,
  Panel,
  PanelHeader,
  PanelTitle,
  RevealGroup,
  Tab,
  TabNav,
  panelClassName,
  tabActiveClass,
} from './index';

describe('Panel', () => {
  it('renders a section by default and merges classes', () => {
    render(<Panel className="extra" data-testid="p" />);
    const el = screen.getByTestId('p');
    expect(el.tagName).toBe('SECTION');
    expect(el).toHaveClass(panelClassName, 'extra');
  });

  it('renders the requested element', () => {
    render(<Panel as="article" data-testid="p" />);
    expect(screen.getByTestId('p').tagName).toBe('ARTICLE');
  });

  it('renders header, title and eyebrow', () => {
    render(
      <PanelHeader simple data-testid="h">
        <PanelTitle>Title</PanelTitle>
        <Eyebrow>Label</Eyebrow>
      </PanelHeader>,
    );
    expect(screen.getByTestId('h').tagName).toBe('HEADER');
    expect(screen.getByText('Title')).toBeInTheDocument();
    expect(screen.getByText('Label').tagName).toBe('SPAN');
  });

  it('only applies the simple header variant when asked', () => {
    render(
      <>
        <PanelHeader data-testid="plain" />
        <PanelHeader simple data-testid="simple" />
      </>,
    );
    expect(screen.getByTestId('simple').className).not.toBe(screen.getByTestId('plain').className);
  });
});

describe('DataTable', () => {
  it('renders a table with rows and actions', () => {
    render(
      <DataTable>
        <tbody>
          <DataTableRow data-testid="active">
            <Actions as="td" data-testid="actions">
              <button>Go</button>
            </Actions>
          </DataTableRow>
          <DataTableRow inactive data-testid="inactive" />
        </tbody>
      </DataTable>,
    );
    expect(screen.getByRole('table')).toBeInTheDocument();
    expect(screen.getByTestId('actions').tagName).toBe('TD');
    expect(screen.getByTestId('inactive').className).not.toBe(
      screen.getByTestId('active').className,
    );
  });

  it('renders Actions as a div by default', () => {
    render(<Actions data-testid="a" />);
    expect(screen.getByTestId('a').tagName).toBe('DIV');
  });
});

describe('Tabs', () => {
  it('marks only the active tab', () => {
    render(
      <TabNav secondary aria-label="views">
        <Tab active>One</Tab>
        <Tab>Two</Tab>
      </TabNav>,
    );
    expect(screen.getByRole('navigation', { name: 'views' })).toBeInTheDocument();
    expect(screen.getByText('One')).toHaveClass(tabActiveClass);
    expect(screen.getByText('Two')).not.toHaveClass(tabActiveClass);
  });

  it('fires clicks', () => {
    const onClick = vi.fn();
    render(<Tab onClick={onClick}>Go</Tab>);
    fireEvent.click(screen.getByText('Go'));
    expect(onClick).toHaveBeenCalledTimes(1);
  });
});

describe('AppShell', () => {
  it('renders the backdrop and children', () => {
    const { container } = render(
      <AppShell>
        <p>content</p>
      </AppShell>,
    );
    expect(screen.getByText('content')).toBeInTheDocument();
    expect(container.querySelectorAll('[aria-hidden="true"]')).toHaveLength(3);
  });

  it('AppHeader shows the logo and no back button without a handler', () => {
    render(<AppHeader />);
    expect(screen.getByAltText('OpenJCockpit')).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('AppHeader back button names its destination and fires', () => {
    const onBack = vi.fn();
    renderWithI18n(<AppHeader onBack={onBack} backTitle="Back to dashboard" />);
    const back = screen.getByRole('button', { name: '← Back to dashboard' });
    expect(back).toHaveAttribute('title', 'Back to dashboard');
    fireEvent.click(back);
    expect(onBack).toHaveBeenCalledTimes(1);
  });

  it('AppHeader back button falls back to the generic label', () => {
    renderWithI18n(<AppHeader onBack={vi.fn()} />);
    expect(screen.getByRole('button', { name: '← Back' })).toBeInTheDocument();
  });

  it('AppHeader supports a custom label and extra actions before the back button', () => {
    render(
      <AppHeader onBack={vi.fn()} backLabel="← Projects">
        <button>Extra</button>
      </AppHeader>,
    );
    const buttons = screen.getAllByRole('button').map((b) => b.textContent);
    expect(buttons).toEqual(['Extra', '← Projects']);
  });
});

describe('decorative components', () => {
  it('Backdrop renders three aria-hidden layers', () => {
    const { container } = render(<Backdrop />);
    expect(container.children).toHaveLength(3);
    for (const child of Array.from(container.children)) {
      expect(child).toHaveAttribute('aria-hidden', 'true');
    }
  });

  it('BrandLogo renders the logo with an optional class', () => {
    render(<BrandLogo className="big" />);
    const img = screen.getByAltText('OpenJCockpit');
    expect(img).toHaveAttribute('src', '/openjcockpit-logo.png');
    expect(img).toHaveClass('big');
  });

  it('BrandLogo shows the square icon when compact', () => {
    render(<BrandLogo compact />);
    expect(screen.getByAltText('OpenJCockpit')).toHaveAttribute('src', '/icon-192.png');
  });

  it('IconBox renders a hidden label with variants', () => {
    render(<IconBox label="AB" variant="hero" compact className="c" />);
    const box = screen.getByText('AB');
    expect(box).toHaveAttribute('aria-hidden', 'true');
    expect(box).toHaveClass('c');
    expect(box.className.split(' ').length).toBeGreaterThan(2);
  });

  it('IconBox has no variant classes by default', () => {
    render(<IconBox label="AB" />);
    expect(screen.getByText('AB').className.split(' ')).toHaveLength(1);
  });
});

describe('RevealGroup', () => {
  it('sets the stagger custom property with a default, on a div', () => {
    render(<RevealGroup data-testid="g">x</RevealGroup>);
    const el = screen.getByTestId('g');
    expect(el.tagName).toBe('DIV');
    expect(el.style.getPropertyValue('--reveal-stagger')).toBe('0.16s');
  });

  it('honours as, stagger, className and merges style', () => {
    render(
      <RevealGroup
        as="section"
        stagger={0.5}
        className="hero"
        style={{ color: 'red' }}
        data-testid="g"
      >
        x
      </RevealGroup>,
    );
    const el = screen.getByTestId('g');
    expect(el.tagName).toBe('SECTION');
    expect(el).toHaveClass('hero');
    expect(el.style.color).toBe('red');
    expect(el.style.getPropertyValue('--reveal-stagger')).toBe('0.5s');
  });
});
