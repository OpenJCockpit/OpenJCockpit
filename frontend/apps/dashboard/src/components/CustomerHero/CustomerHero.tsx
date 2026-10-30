import type { Project, Workspace } from '@openjcockpit/api';
import { GitStatusBadge } from '@openjcockpit/project';
import { useTranslation } from '@openjcockpit/i18n';
import { Eyebrow, IconBox } from '@openjcockpit/ui';
import styles from './CustomerHero.module.scss';

interface Props {
  selectedProject: Project | null;
  workspace: Workspace;
}

/**
 * Turns a stored git URL (HTTPS or SCP-style SSH) into the repository's web page,
 * or undefined when it does not resolve to an http(s) address.
 */
export function toRepositoryPageUrl(gitUrl: string | undefined): string | undefined {
  const trimmed = gitUrl?.trim();
  if (!trimmed) return undefined;
  const scp = /^[\w.-]+@([\w.-]+):(.+)$/.exec(trimmed);
  const candidate = (scp ? `https://${scp[1]}/${scp[2]}` : trimmed).replace(/\.git\/?$/, '');
  try {
    const url = new URL(candidate);
    if (url.protocol !== 'https:' && url.protocol !== 'http:') return undefined;
    url.username = '';
    url.password = '';
    return url.toString();
  } catch {
    return undefined;
  }
}

/** The GitHub mark, drawn in the link's text colour. */
function GitHubIcon() {
  return (
    <svg
      className={styles.repositoryIcon}
      viewBox="0 0 16 16"
      width="14"
      height="14"
      aria-hidden="true"
      focusable="false"
    >
      <path
        fill="currentColor"
        d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"
      />
    </svg>
  );
}

export function CustomerHero({ selectedProject, workspace }: Props) {
  const { t } = useTranslation('dashboard');
  const repositoryUrl = toRepositoryPageUrl(selectedProject?.gitUrl);

  return (
    <section className={styles.workspaceHero}>
      <div className="customer-block">
        <div className={styles.customerTitle}>
          <IconBox label="◇" variant="hero" />
          <div>
            <Eyebrow>{t('siteHeader.nav.platform')}</Eyebrow>
            <h1>
              {selectedProject?.name ?? workspace.customer.name}
              {selectedProject && <GitStatusBadge status={selectedProject.gitStatus} compact />}
            </h1>
            {repositoryUrl && (
              <a
                className={styles.repositoryLink}
                href={repositoryUrl}
                target="_blank"
                rel="noreferrer"
                data-testid="project-repository-link"
              >
                <GitHubIcon />
                {repositoryUrl}
              </a>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}
