import { SpecQueueSettings, WithSpecQueueTranslations } from '@openjcockpit/spec-queue';

/** Settings section; the host loads it lazily, so it registers its own translations. */
export default function SpecQueueSettingsSection() {
  return (
    <WithSpecQueueTranslations>
      <SpecQueueSettings />
    </WithSpecQueueTranslations>
  );
}
