import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { SpecMarkdown, parseBlocks } from './SpecMarkdown';

describe('parseBlocks', () => {
  it('parses headings, lists, code and paragraphs', () => {
    const blocks = parseBlocks('# T\n\ntext\nmore\n\n- a\n- b\n\n```\ncode\n```');
    expect(blocks.map((b) => b.kind)).toEqual(['heading', 'paragraph', 'list', 'code']);
  });

  it('joins consecutive lines into one paragraph but splits on blank lines', () => {
    expect(parseBlocks('one\ntwo\n\nthree')).toEqual([
      { kind: 'paragraph', text: 'one two' },
      { kind: 'paragraph', text: 'three' },
    ]);
  });

  it('starts a new paragraph after a list or heading', () => {
    expect(parseBlocks('- a\ntext').map((b) => b.kind)).toEqual(['list', 'paragraph']);
  });

  it('keeps unordered and ordered lists apart and merges same-kind items', () => {
    expect(parseBlocks('- a\n* b\n1. c\n2) d\n+ e')).toEqual([
      { kind: 'list', ordered: false, items: ['a', 'b'] },
      { kind: 'list', ordered: true, items: ['c', 'd'] },
      { kind: 'list', ordered: false, items: ['e'] },
    ]);
  });

  it('reads an unterminated code fence to the end', () => {
    expect(parseBlocks('```\na\n\nb')).toEqual([{ kind: 'code', text: 'a\n\nb' }]);
  });

  it('only treats 1-6 hashes followed by a space as a heading', () => {
    expect(parseBlocks('####### x\n#nospace').map((b) => b.kind)).toEqual(['paragraph']);
    expect(parseBlocks('###### six')).toEqual([{ kind: 'heading', level: 6, text: 'six' }]);
  });
});

describe('SpecMarkdown', () => {
  it('omits the leading H1 and renders the remaining blocks', () => {
    const { container } = render(
      <SpecMarkdown
        content={
          '# Title\n\n## Sub\n\nUses `code` and **bold** text\n\n- a\n- b\n\n1. x\n\n```\nraw\n```'
        }
      />,
    );
    expect(container.textContent).not.toContain('Title');
    expect(container.querySelector('p[data-level="2"]')?.textContent).toBe('Sub');
    expect(container.querySelector('p code')?.textContent).toBe('code');
    expect(container.querySelector('p strong')?.textContent).toBe('bold');
    expect(container.querySelectorAll('ul li')).toHaveLength(2);
    expect(container.querySelectorAll('ol li')).toHaveLength(1);
    expect(container.querySelector('pre code')?.textContent).toBe('raw');
  });

  it('keeps a first block that is not an H1, and caps heading level at 3', () => {
    const { container } = render(<SpecMarkdown content={'text\n\n##### Deep'} />);
    expect(container.textContent).toContain('text');
    expect(container.querySelector('p[data-level="3"]')?.textContent).toBe('Deep');
  });

  it('keeps a leading heading deeper than H1', () => {
    const { container } = render(<SpecMarkdown content="## Kept" />);
    expect(container.textContent).toBe('Kept');
  });
});
