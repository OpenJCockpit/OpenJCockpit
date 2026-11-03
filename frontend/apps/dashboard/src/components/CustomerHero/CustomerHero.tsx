import type { Project, Workspace } from '@openjcockpit/api';
import { GitStatusBadge } from '@openjcockpit/project';
import { useTranslation } from '@openjcockpit/i18n';
import { Eyebrow, Icon, IconBox } from '@openjcockpit/ui';
import styles from './CustomerHero.module.scss';

interface Props {
  selectedProject: Project | null;
  workspace?: Workspace;
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

export function CustomerHero({ selectedProject, workspace }: Props) {
  const { t } = useTranslation('dashboard');
  const repositoryUrl = toRepositoryPageUrl(selectedProject?.gitUrl);

  return (
    <section className={styles.workspaceHero}>
      <div className="customer-block">
        <div className={styles.customerTitle}>
          <IconBox label={<Icon name="cube" />} variant="hero" />
          <div>
            <Eyebrow>{t('siteHeader.nav.platform')}</Eyebrow>
            <h1>
              {selectedProject?.name ?? workspace?.customer.name}
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
                <Icon name="github" className={styles.repositoryIcon} />
                {repositoryUrl}
              </a>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}
