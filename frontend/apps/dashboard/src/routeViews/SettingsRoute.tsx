import { ProjectSettings } from '../lazyViews';
import { useGoBack, type ShellProps } from './shell';

export function SettingsRoute({ selectedProject }: Pick<ShellProps, 'selectedProject'>) {
  return <ProjectSettings onBack={useGoBack(selectedProject)} />;
}
