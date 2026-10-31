import type { PreflightError } from '@openjcockpit/api';
import { useTranslation, type TFunction } from '@openjcockpit/i18n';
import { Button } from '@openjcockpit/ui';
import styles from './PreflightAlert.module.scss';

const PREFLIGHT_ERROR_CODES = [
  'DOCKER_UNAVAILABLE',
  'GIT_REPOSITORY_UNAVAILABLE',
  'NO_SELECTED_PROJECT',
  'PROJECT_INACTIVE',
  'GIT_AUTH_FAILED',
  'GIT_URL_MISSING',
  'GIT_REPOSITORY_NOT_FOUND',
  'TIMEOUT',
] as const;

function isKnownCode(code: string): code is (typeof PREFLIGHT_ERROR_CODES)[number] {
  return (PREFLIGHT_ERROR_CODES as readonly string[]).includes(code);
}

function describePreflightError(error: PreflightError, t: TFunction<'dashboard'>): string {
  return isKnownCode(error.code)
    ? t(`preflight.errors.${error.code}`)
    : t('preflight.unknown', { message: error.message });
}

const GIT_RELATED_CODES = new Set([
  'GIT_REPOSITORY_UNAVAILABLE',
  'GIT_AUTH_FAILED',
  'GIT_URL_MISSING',
  'GIT_REPOSITORY_NOT_FOUND',
  'NO_SELECTED_PROJECT',
  'PROJECT_INACTIVE',
]);

export function PreflightAlert({
  errors,
  onDismiss,
  onOpenProjectSettings,
}: {
  errors: PreflightError[];
  onDismiss: () => void;
  onOpenProjectSettings: () => void;
}) {
  const { t } = useTranslation('dashboard');
  const hasGitIssue = errors.some((e) => GIT_RELATED_CODES.has(e.code));

  return (
    <div className={styles.preflightAlert} role="alert">
      <span className={styles.preflightAlertIcon} aria-hidden="true">
        ⚠
      </span>
      <div className={styles.preflightAlertBody}>
        <h3 className={styles.preflightAlertTitle}>{t('preflight.title')}</h3>
        <ul className={styles.preflightAlertList}>
          {errors.map((error, index) => (
            <li key={`${error.code}-${index}`}>{describePreflightError(error, t)}</li>
          ))}
        </ul>
        <div className={styles.preflightAlertActions}>
          {hasGitIssue && (
            <Button variant="primary" onClick={onOpenProjectSettings}>
              {t('preflight.checkGitSettings')}
            </Button>
          )}
          <Button variant="link" onClick={onDismiss}>
            {t('preflight.dismiss')}
          </Button>
        </div>
      </div>
    </div>
  );
}
