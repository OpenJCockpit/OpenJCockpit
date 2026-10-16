# Local agents

Agent definitions in this folder are listed in the dashboard under **Workflow design → Agents → Local**.

- One agent per Markdown file (`*.md`, any depth). Subfolders become the hierarchy shown in the
  dashboard, e.g. `agents/delivery/analysis/business-analyst.md` → *delivery / analysis*.
- `README.md` files are ignored. Symlinks are not followed.
- Format: YAML frontmatter followed by the agent's instructions in Markdown.

```markdown
---
id: my-agent                 # required, lowercase kebab-case, unique across this folder
name: "My Agent"             # optional display name
version: "1.0.0"             # required
description: "What it does"  # optional
model: agent-chat            # required, a configured model alias
skills: []                   # optional skill ids
tools: []                    # optional tool ids (granted only server-side)
guardrails: [pii-protection] # optional
---
# Role
...
```

Invalid files are still listed, marked with the problem. Agents served over A2A by other providers
(e.g. LiteLLM) are listed under their provider's header instead.
