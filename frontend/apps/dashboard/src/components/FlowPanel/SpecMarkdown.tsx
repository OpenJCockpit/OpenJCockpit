import type { ReactNode } from 'react';
import styles from './SpecPreview.module.scss';

type Block =
  | { kind: 'heading'; level: number; text: string }
  | { kind: 'code'; text: string }
  | { kind: 'list'; ordered: boolean; items: string[] }
  | { kind: 'paragraph'; text: string };

const ORDERED_TAG = 'ol';
const UNORDERED_TAG = 'ul';
const ORDERED_MARKER = /^\d+[.)]\s+/;

function listItem(line: string): { ordered: boolean; text: string } | undefined {
  const trimmed = line.trim();
  if (['- ', '* ', '+ '].some((marker) => trimmed.startsWith(marker))) {
    return { ordered: false, text: trimmed.slice(2) };
  }
  const ordered = ORDERED_MARKER.exec(trimmed);
  return ordered ? { ordered: true, text: trimmed.slice(ordered[0].length) } : undefined;
}

function headingLevel(line: string): number {
  let hashes = 0;
  while (line[hashes] === '#') hashes++;
  return hashes > 0 && hashes <= 6 && line[hashes] === ' ' ? hashes : 0;
}

/** Splits markdown into the few block kinds a spec preview needs. */
export function parseBlocks(markdown: string): Block[] {
  const blocks: Block[] = [];
  const queue = markdown.split('\n');
  let previous = '';
  for (let line = queue.shift(); line !== undefined; line = queue.shift()) {
    const trimmed = line.trim();
    const continues = previous !== '';
    previous = trimmed;
    if (!trimmed) continue;

    if (trimmed.startsWith('```')) {
      const code: string[] = [];
      for (
        let next = queue.shift();
        next !== undefined && !next.trim().startsWith('```');
        next = queue.shift()
      ) {
        code.push(next);
      }
      blocks.push({ kind: 'code', text: code.join('\n') });
      continue;
    }
    const level = headingLevel(trimmed);
    if (level > 0) {
      blocks.push({ kind: 'heading', level, text: trimmed.slice(level + 1).trim() });
      continue;
    }
    const item = listItem(line);
    const last = blocks[blocks.length - 1];
    if (item) {
      if (last?.kind === 'list' && last.ordered === item.ordered) last.items.push(item.text);
      else blocks.push({ kind: 'list', ordered: item.ordered, items: [item.text] });
      continue;
    }
    if (last?.kind === 'paragraph' && continues) last.text += ` ${trimmed}`;
    else blocks.push({ kind: 'paragraph', text: trimmed });
  }
  return blocks;
}

/** `code` and **bold** spans. Content is rendered as React nodes, never as HTML. */
function inline(text: string): ReactNode[] {
  return text
    .split('`')
    .map((part, i) =>
      i % 2 === 1 ? (
        <code key={i}>{part}</code>
      ) : (
        part
          .split('**')
          .map((chunk, j) => (j % 2 === 1 ? <strong key={`${i}-${j}`}>{chunk}</strong> : chunk))
      ),
    );
}

/** A light, safe markdown rendering for spec previews (headings, lists, code, emphasis). */
export function SpecMarkdown({ content }: { content: string }) {
  const blocks = parseBlocks(content);
  // The title is already on the card, so the leading H1 is not repeated.
  const shown = blocks[0]?.kind === 'heading' && blocks[0].level === 1 ? blocks.slice(1) : blocks;
  return (
    <div className={styles.markdown}>
      {shown.map((block, i) => {
        switch (block.kind) {
          case 'heading':
            return (
              <p key={i} className={styles.mdHeading} data-level={Math.min(block.level, 3)}>
                {inline(block.text)}
              </p>
            );
          case 'code':
            return (
              <pre key={i}>
                <code>{block.text}</code>
              </pre>
            );
          case 'list': {
            const List = block.ordered ? ORDERED_TAG : UNORDERED_TAG;
            return (
              <List key={i}>
                {block.items.map((item, j) => (
                  <li key={j}>{inline(item)}</li>
                ))}
              </List>
            );
          }
          default:
            return <p key={i}>{inline(block.text)}</p>;
        }
      })}
    </div>
  );
}
