import type { CSSProperties } from 'react';
import { useNavigate } from 'react-router';
import { useTranslation } from '@openjcockpit/i18n';
import type { WidgetDefinition } from '@openjcockpit/hub-contract';
import { Panel } from '@openjcockpit/ui';
import { RemoteSlot } from '../federation/RemoteSlot';
import type { LayoutItem } from './homeLayout';
import gridStyles from './HomeGrid.module.scss';
import styles from './WidgetCard.module.scss';

/** A widget as it appears on the hub: its title, its content, and a link to the widget's page. */
interface Props {
  widget: WidgetDefinition;
  item: LayoutItem;
}

export function WidgetCard({ widget, item }: Props) {
  const { t } = useTranslation('dashboard');
  const navigate = useNavigate();
  const title = String(t(widget.titleKey as never));

  return (
    <Panel
      as="article"
      className={gridStyles.tile}
      // eslint-disable-next-line no-restricted-syntax -- runtime grid spans passed as custom properties
      style={{ '--cols': item.cols, '--rows': item.rows } as CSSProperties}
      data-testid={`widget-${widget.id}`}
      data-cols={item.cols}
      data-rows={item.rows}
    >
      <header className={gridStyles.tileHeader}>
        <h2 className={gridStyles.tileTitle}>{title}</h2>
      </header>
      <div className={gridStyles.tileBody}>
        <RemoteSlot item={widget} />
      </div>
      <footer className={styles.tileFooter}>
        <button
          type="button"
          className={styles.openLink}
          onClick={() => void navigate(widget.link)}
          data-testid={`widget-open-${widget.id}`}
        >
          {t('home.view', { name: title })}
        </button>
      </footer>
    </Panel>
  );
}
