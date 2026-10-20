import type { Project, Workspace } from '@openjcockpit/api';
import { GitStatusBadge } from '../GitStatusBadge/GitStatusBadge';
import { IconBox } from '../IconBox/IconBox';
import { StackServiceCard } from '../StackServiceCard/StackServiceCard';
import styles from './CustomerHero.module.scss';

interface Props {
  selectedProject: Project | null;
  workspace: Workspace;
}

export function CustomerHero({ selectedProject, workspace }: Props) {
  return (
    <section className={styles.workspaceHero}>
      <div className="customer-block">
        <div className={styles.customerTitle}>
          <IconBox label="◇" variant="hero" />
          <div>
            <h1>
              Customer: {selectedProject?.name ?? workspace.customer.name}
              {selectedProject && <GitStatusBadge status={selectedProject.gitStatus} compact />}
            </h1>
            <div className={styles.customerMeta}>
              <span>Sector: {workspace.customer.sector}</span>
              <span>Environment: {workspace.customer.environment}</span>
              <span>Last active: {workspace.customer.lastActive}</span>
            </div>
          </div>
        </div>
      </div>

      <div className={styles.stackStatus}>
        {workspace.stack.map((service, index) => (
          <StackServiceCard
            key={service.name}
            service={service}
            variant={index === 0 ? 'hub' : 'spring'}
          />
        ))}
      </div>
    </section>
  );
}
