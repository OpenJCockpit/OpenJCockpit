import { Suspense } from 'react';
import { AddToQueueDialog, SpecFilesDashboard } from '../lazyViews';
import { RouteHeader } from './RouteHeader';
import { useProject } from './ProjectContext';
import { useGoBack, type ShellProps } from './shell';

export function SpecFilesRoute({ shell }: { shell: ShellProps }) {
  const project = useProject();
  return (
    <SpecFilesDashboard
      project={project}
      onBack={useGoBack(project)}
      header={<RouteHeader active="specFiles" shell={shell} />}
      renderAddToQueue={({ project, fixedSpecFile, onAdded, onCancel }) => (
        <Suspense fallback={null}>
          <AddToQueueDialog
            project={project}
            fixedSpecFile={fixedSpecFile}
            onAdded={onAdded}
            onCancel={onCancel}
          />
        </Suspense>
      )}
    />
  );
}
