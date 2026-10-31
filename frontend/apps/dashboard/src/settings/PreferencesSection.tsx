import { useContext } from 'react';
import {
  LOCALE_NAMES,
  type Locale,
  SUPPORTED_LOCALES,
  useLocale,
  useTranslation,
} from '@openjcockpit/i18n';
import { HubThemeContext, isThemePreference, THEME_PREFERENCES } from '@openjcockpit/hub-contract';
import { Select } from '@openjcockpit/ui';
import styles from './PreferencesSection.module.scss';

/** Per-user display preferences: the language and the colour theme. */
export default function PreferencesSection() {
  const { t } = useTranslation('dashboard');
  const { locale, setLocale } = useLocale();
  // Absent in a standalone harness, where there is no host theme to change.
  const theme = useContext(HubThemeContext);

  return (
    <div className={styles.preferences}>
      <Select
        block
        label={t('siteHeader.language')}
        value={locale}
        options={SUPPORTED_LOCALES.map((code) => ({
          value: code,
          label: LOCALE_NAMES[code],
          lang: code,
        }))}
        // A failed locale download keeps the current language; the select stays on it.
        onChange={(code) => void setLocale(code as Locale).catch(() => undefined)}
        data-testid="locale-select"
      />
      {theme && (
        <Select
          block
          label={t('siteHeader.theme')}
          value={theme.preference}
          options={THEME_PREFERENCES.map((preference) => ({
            value: preference,
            label: t(`siteHeader.themes.${preference}`),
          }))}
          onChange={(value) => isThemePreference(value) && theme.setPreference(value)}
          data-testid="theme-select"
        />
      )}
    </div>
  );
}
