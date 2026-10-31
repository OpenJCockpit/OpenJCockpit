import { useState } from 'react';
import { useTranslation } from '@openjcockpit/i18n';
import { fieldHintClass, formInputClass, Button } from '@openjcockpit/ui';
import WorkflowDesignStyles from '../../WorkflowDesign.module.scss';

export type PromptGeneratorKind = 'agent' | 'subagent' | 'skill';

export function PromptGenerator({
  kind,
  onGenerate,
}: {
  kind: PromptGeneratorKind;
  onGenerate: (prompt: string) => Promise<void>;
}) {
  const { t } = useTranslation('workflow');
  const [prompt, setPrompt] = useState('');
  const [generating, setGenerating] = useState(false);

  async function handleGenerate() {
    setGenerating(true);
    try {
      await onGenerate(prompt.trim());
    } finally {
      setGenerating(false);
    }
  }

  return (
    <div className={WorkflowDesignStyles.workflowPromptGenerator}>
      <label>
        {t('promptGenerator.label', { kind: t(`promptGenerator.kind.${kind}`) })}
        <textarea
          className={formInputClass}
          rows={3}
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          placeholder={t(`promptGenerator.example.${kind}`)}
        />
      </label>
      <Button variant="primary" onClick={() => void handleGenerate()} disabled={generating}>
        {generating ? t('promptGenerator.generating') : t('promptGenerator.generate')}
      </Button>
      <p className={fieldHintClass}>
        {t('promptGenerator.hint', { kind: t(`promptGenerator.kind.${kind}`) })}
      </p>
    </div>
  );
}
