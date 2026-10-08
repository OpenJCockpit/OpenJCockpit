# Metafactory AI Delivery Factory — Frontend

Minimalist, futuristic React/Vite frontend for the Metafactory workspace.

This version follows the calmer cockpit layout from the latest reference image:

- selected customer at the top;
- compact spec list on the left;
- central Embabel agent flow;
- compact quality status on the right;
- summary of open pull requests;
- summary of evidence;
- responsive layout for smaller screens.

## Getting started

```bash
npm install
npm run dev
```

By default, the frontend tries to fetch the workspace via:

```text
/api/workspaces/noordzee-logistics
```

If the backend is not reachable, the application falls back to `src/mockWorkspace.ts`.

## Build

```bash
npm run build
```

## Code quality

This app uses Node **22** (see `.nvmrc`, also used by the `Dockerfile` with `node:22-alpine`) and npm. Install the tooling with `npm install`.

The following commands are available:

- `npm run lint` — checks the code with ESLint (reports an error on new findings, exit code 0 on a clean run).
- `npm run lint:fix` — applies ESLint autofixes; to be run locally by the developer only, never in an automated check.
- `npm run format` — reformats files with Prettier (writes changes to disk).
- `npm run format:check` — checks formatting with Prettier without modifying files.

### Editor settings (format-on-save)

Install the Prettier extension in your editor, enable "format on save" and set Prettier as the default formatter for TypeScript/TSX/CSS files. Because `.editorconfig` and `.prettierrc.json` use the same values (indentation, line endings, charset, final newline), the editor and the command line produce identical formatting.

### Known limitations

- The existing code has deliberately **not** been reformatted in one go; `npm run format:check` therefore still reports a number of files that do not conform to the Prettier style. Only run `npm run format` on files you are already changing yourself, not on the whole folder.
- There is **no CI check** for lint/format in this delivery — enforcement is, for now, the responsibility of the developer, until a separate CI workflow is added for it.
- The `jsx-a11y` rules only cover part of the WCAG criteria, and `prefers-reduced-motion` cannot be checked automatically. This remains a point for manual review.

## Integration

Place this folder as `frontend/` in the monorepo. The API contracts remain the same as in the earlier starter.
