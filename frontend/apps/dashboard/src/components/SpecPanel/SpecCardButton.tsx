import type { SpecFile } from '@openjcockpit/api';
import { useTranslation } from '@openjcockpit/i18n';
import { IconBox, specStyles, cx } from '@openjcockpit/ui';

interface Props {
  spec: SpecFile;
  selected: boolean;
  onSelect: (id: string) => void;
}

/** A selectable card for one spec file in the spec list. */
export function SpecCardButton({ spec, selected, onSelect }: Props) {
  const { t } = useTranslation('dashboard');
  return (
    <button
      className={cx(specStyles.specCard, selected && specStyles.selected)}
      onClick={() => onSelect(spec.id)}
      aria-pressed={selected}
    >
      <IconBox label="📄" compact className={specStyles.icon} />
      <div>
        <strong>{spec.fileName}</strong>
        <span>{t('specPanel.lastModified', { value: spec.lastChanged })}</span>
      </div>
      {selected && <span className={specStyles.activeRail} aria-hidden="true" />}
    </button>
  );
}
