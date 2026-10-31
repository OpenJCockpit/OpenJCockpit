import { SkillsHubSettings } from '../SkillsHubSettings/SkillsHubSettings';
import { WithTranslations } from '../registerTranslations';

/** Settings section; the host loads it lazily, so it registers its own translations. */
export default function SkillsHubSettingsSection() {
  return (
    <WithTranslations>
      <SkillsHubSettings />
    </WithTranslations>
  );
}
