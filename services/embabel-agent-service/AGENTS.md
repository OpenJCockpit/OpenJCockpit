# Spec-driven workflow rules

All agentic AI work in this project must follow the spec-driven workflow: every feature is
specified as an executable contract before an agent plans or implements it.

Before planning or implementing a feature, read the feature's `spec.md` metadata.

Every feature must have its own folder under `specs/` using the pattern:

`NNN-feature-slug/`

Each feature folder must contain:

- `spec.md`
- `plan.md`
- `tasks.md`
- `review.md`

The only Markdown file allowed directly inside `specs/` is `_index.md`.

Flat spec structures are strictly forbidden.

The agent must stop and ask for human approval when:

- `contains_personal_data: true` and `llm_strategy.mode` is `public_cloud`
- `contains_secrets: true`
- `dpia_required: true`
- `dpia_required: unknown` and `privacy_risk: high`
- the task mutates customer data
- the task sends external messages
- the task changes authentication, authorization, logging, retention, or data exports

The agent must not send secrets, credentials, tokens, production data, special category data, or raw personal data to public LLMs.

## Where things live

- Feature specs: `specs/NNN-feature-slug/` (see `specs/_index.md` for the index)
- Templates: `templates/specs/*.template.md`
- Structure is enforced by `SpecStructureValidator` and initialized by `SpecWorkflowInitializer`
  (runs at application startup; also enforced by JUnit tests via `mvn test`).
