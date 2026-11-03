import { describe, expect, it } from 'vitest';
import { parseBlocks } from './SpecMarkdown';

describe('parseBlocks', () => {
  it('parses headings, lists, code and paragraphs', () => {
    const blocks = parseBlocks('# T\n\ntext\nmore\n\n- a\n- b\n\n```\ncode\n```');
    expect(blocks.map((b) => b.kind)).toEqual(['heading', 'paragraph', 'list', 'code']);
  });
});
