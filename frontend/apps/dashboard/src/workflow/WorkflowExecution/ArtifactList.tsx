import styles from './WorkflowExecution.module.scss';
/**
 * A branch entry is always a bare branch name (never a URL) as stored by the backend, so
 * it is rendered as a plain text label. A pull-request entry is always a genuine URL and
 * is rendered as a clickable link.
 */
interface ArtifactEntry {
  label: string;
  url?: string;
}

export function parseArtifactLinks(artifacts: string[]): ArtifactEntry[] {
  const entries: ArtifactEntry[] = [];
  for (const artifact of artifacts) {
    if (artifact.startsWith('git-branch:')) {
      const value = artifact.slice('git-branch:'.length).trim();
      if (value) entries.push({ label: `Branch: ${value}` });
    } else if (artifact.startsWith('pull-request:')) {
      const value = artifact.slice('pull-request:'.length).trim();
      if (value) entries.push({ label: 'Pull request', url: value });
    }
  }
  return entries;
}

interface Props {
  entries: ArtifactEntry[];
}

export function ArtifactList({ entries }: Props) {
  if (entries.length === 0) return null;
  return (
    <div className={styles.workflowArtifacts}>
      {entries.map((entry) =>
        entry.url ? (
          <a key={entry.label} href={entry.url} target="_blank" rel="noopener noreferrer">
            {entry.label}
          </a>
        ) : (
          <span key={entry.label} className="workflow-artifact-label">
            {entry.label}
          </span>
        ),
      )}
    </div>
  );
}
