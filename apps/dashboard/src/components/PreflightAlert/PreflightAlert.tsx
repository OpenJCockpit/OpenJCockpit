import './PreflightAlert.scss';
import type { PreflightError } from '../../types';

const PREFLIGHT_ERROR_MESSAGES: Record<string, string> = {
  DOCKER_UNAVAILABLE:
    'Docker is not available. Please make sure Docker is running and the application is allowed to start containers.',
  GIT_REPOSITORY_UNAVAILABLE:
    'The Git repository is not reachable. Please check the project Git URL or credentials.',
  NO_SELECTED_PROJECT:
    'No project is selected. Please choose a project before starting the workflow.',
  PROJECT_INACTIVE: 'The selected project is inactive and cannot be used to start a workflow.',
  GIT_AUTH_FAILED: 'Git authentication failed. Please update the project credentials.',
  GIT_URL_MISSING: 'The selected project does not have a Git URL configured.',
  GIT_REPOSITORY_NOT_FOUND: 'The Git repository could not be found.',
  TIMEOUT: 'The pre-flight check timed out. Please try again or check the infrastructure.',
};

function describePreflightError(error: PreflightError): string {
  return PREFLIGHT_ERROR_MESSAGES[error.code] ?? `Pre-flight check failed: ${error.message}`;
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
  const hasGitIssue = errors.some((e) => GIT_RELATED_CODES.has(e.code));

  return (
    <div className="preflight-alert" role="alert">
      <span className="preflight-alert__icon" aria-hidden="true">
        ⚠
      </span>
      <div className="preflight-alert__body">
        <h3 className="preflight-alert__title">Pre-flight check failed</h3>
        <ul className="preflight-alert__list">
          {errors.map((error, index) => (
            <li key={`${error.code}-${index}`}>{describePreflightError(error)}</li>
          ))}
        </ul>
        <div className="preflight-alert__actions">
          {hasGitIssue && (
            <button className="button button--start" onClick={onOpenProjectSettings}>
              Check Git settings
            </button>
          )}
          <button className="link-button" onClick={onDismiss}>
            Dismiss
          </button>
        </div>
      </div>
    </div>
  );
}
