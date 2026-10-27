import { Suspense } from 'react';
import { AddToQueueDialog, SpecFilesDashboard } from '../lazyViews';
import { RouteHeader } from './RouteHeader';
import { useGoBack, type ShellProps } from './shell';

export function SpecFilesRoute({ shell }: { shell: ShellProps }) {
  return (
    <SpecFilesDashboard
      project={shell.selectedProject}
      onBack={useGoBack(shell.selectedProject)}
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
