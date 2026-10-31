import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { useTranslation } from 'react-i18next';
import { renderWithPseudoI18n, untranslatedText } from './pseudoLocale';

function Sample() {
  const { t } = useTranslation();
  return (
    <div>
      <p>{t('back')}</p>
      <p>Plain English</p>
      <button aria-label="Close dialog">×</button>
      <span>42</span>
    </div>
  );
}

describe('untranslatedText', () => {
  it('lists text and attributes that bypassed i18n, and ignores wrapped and letterless text', () => {
    renderWithPseudoI18n(<Sample />);
    expect(screen.getByText(/^\[.*~+\]$/)).toBeInTheDocument();
    expect(untranslatedText(document.body)).toEqual(['Plain English', 'Close dialog']);
  });

  it('ignores script and style text', () => {
    renderWithPseudoI18n(
      <div>
        <script>{'var greeting = "Hello there";'}</script>
        <style>{'.a { content: "Hello there"; }'}</style>
      </div>,
    );
    expect(untranslatedText(document.body)).toEqual([]);
  });

  it('skips allowed strings and patterns', () => {
    renderWithPseudoI18n(<Sample />);
    expect(untranslatedText(document.body, ['Plain English', /^Close/])).toEqual([]);
  });
});
