import type { StackServiceStatus } from '../../types';
import { StackServiceIcon } from '../StackServiceIcon/StackServiceIcon';

interface Props {
  service: StackServiceStatus;
  variant: 'hub' | 'spring';
}

export function StackServiceCard({ service, variant }: Props) {
  return (
    <article className="stack-card">
      <StackServiceIcon variant={variant} />
      <div>
        <strong>{service.name}</strong>
        <span>{service.description}</span>
      </div>
      <i aria-label={service.status} />
    </article>
  );
}
