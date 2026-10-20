import { useState } from 'react';
import { fieldHintClass, formInputClass, Button } from '@openjcockpit/ui';
import WorkflowDesignStyles from '../../WorkflowDesign.module.scss';

export type PromptGeneratorKind = 'agent' | 'subagent' | 'skill';

const PROMPT_EXAMPLES: Record<PromptGeneratorKind, string> = {
  agent:
    'An agent that owns the full code review lifecycle for a pull request: it pulls the diff and the ' +
    'linked ticket, delegates static security analysis to a security-scanner subagent and style checks to a ' +
    'linting subagent, synthesizes their findings into a single pass/fail verdict with a prioritized comment ' +
    'list, and posts the review back to the PR.',
  subagent:
    'A subagent that performs static security analysis on a code diff: given the changed files and the ' +
    'language, it checks for injection risks, hardcoded secrets, and unsafe deserialization, then returns a ' +
    'structured list of findings with file, line, severity, and remediation advice for its parent agent.',
  skill:
    'A skill that turns a pull request diff into a structured changelog entry: given the unified diff and ' +
    'commit messages as input, it returns a short human-readable summary, a list of breaking changes, and a ' +
    'suggested semantic version bump (patch/minor/major) as output.',
};

export function PromptGenerator({
  kind,
  onGenerate,
}: {
  kind: PromptGeneratorKind;
  onGenerate: (prompt: string) => Promise<void>;
}) {
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
        Describe the {kind} you want in plain language
        <textarea
          className={formInputClass}
          rows={3}
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          placeholder={PROMPT_EXAMPLES[kind]}
        />
      </label>
      <Button variant="primary" onClick={() => void handleGenerate()} disabled={generating}>
        {generating ? '⟳ Generating…' : '✨ Generate draft'}
      </Button>
      <p className={fieldHintClass}>
        Draft generation uses the platform's LLM. The more specific you are about inputs, outputs,
        and delegation (see the example above), the higher-quality the draft. Leave the field empty
        to generate an example {kind} instead. Review and edit the generated draft before saving.
      </p>
    </div>
  );
}
