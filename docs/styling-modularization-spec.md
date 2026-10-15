# Spec: Modularize dashboard styling

Status: decisions made · Scope: `apps/dashboard` only (`apps/landing` is a separate app, kept out) · Out of scope: `docs-site/styles.css`, `infrastructure/themes/.../openjcockpit-login.css` (separate deploy targets, no shared build)

## 1. Current state (measured)

| Item | Finding |
| --- | --- |
| Global sheets | `apps/dashboard/src/styles.scss` (929 lines, 71 classes) and `app.scss` (295 lines, 25 classes), imported from `main.tsx` / `App.tsx` |
| Colocated sheets | ~20 plain `Component.scss` files already exist (e.g. `StatusChip.scss`, `WorkflowDesign.scss`). They are **global, unscoped CSS** imported for side effects (25 `import '*.scss'`, 0 `*.module.scss`) |
| Inline styles | 16 `style={{…}}` in dashboard, in 7 files. 12 are static and belong in SCSS (`ProjectList`, `ProjectSettings`, `ProjectForm`, `CredentialsForm`, `SkillsHubSettings`, `GitStatusBadge`). The `Tooltip` one is dynamic and legitimate. |
| Design tokens | Defined in `:root` of `styles.scss`, but incomplete (see below). 232 hard-coded `rgba()`/hex values vs 110 `var()` uses |
| Tooling | Vite 6 + `sass` 1.104, React 18. No Tailwind, CSS-in-JS or classnames helper. CSS Modules work out of the box with Vite |

### Problems found

1. **Mixed ownership.** Of the 96 classes in the two global files, 15 + 9 are used by exactly one component and should live next to it. Examples: `.flow-panel`, `.pr-card`, `.quality-panel`, `.status-chip`, `.stack-card`, `.avatar`, `.spec-content`, `.app-shell`, `.connector`.
2. **Split definitions.** `.pr-card` is defined in `styles.scss:606` and `app.scss:215/221`. `.panel-header` and `.panel-header--quality` are split across the two files. `.primary-link` and `.status-bar` are each defined twice inside `styles.scss` (lines 199/572 and 112/630). Cascade order currently decides the winner, so moving rules carelessly changes rendering.
3. **Dead CSS.** No `.tsx`/`.ts` reference for: `agent-flow`, `button--outline`, `primary-link`, `selected-button`, `settings-form--disabled`, `agent-node`, `flow-subtitle`. Verify, then delete.
4. **Misnamed shared primitives.** `settings-*` classes are the de-facto form and table kit, used far beyond settings: `settings-table` in 5 workflow design tables, `settings-input` in 4 `FormFields`, 3 modals and 3 panels, `settings-desc` in 23 files. `back-button` (17 files) is the generic secondary button. `auth-loading` (11 files) is the generic loading/empty message. `button--start` (8 files) is the primary action. The names mislead and make deletion unsafe.
5. **Undefined tokens.** `--font-mono` (6 uses), `--danger` (2 uses, with a `#ff4444` fallback) and `--text` (3 uses) are referenced but not declared in the dashboard `:root`. They only work through per-use fallbacks.
6. **Tokens only partly used.** Panel backgrounds, borders and shadows repeat the same `rgba(124, 222, 255, …)`, `rgba(39, 231, 255, …)` values. There is no spacing, radius or font-size scale, so inline styles invent numbers (`gap: 12`, `padding: '32px 48px'`, `fontSize: 11/12/13/14`).

## 2. Goals and non-goals

Goals
- Every component owns its styles; global CSS is limited to reset, tokens, base elements and genuinely shared primitives.
- No static inline styles. Only values computed at runtime may stay inline.
- One token source of truth, fully declared, used instead of literals.
- Zero visual change per phase (this is a refactor).

Non-goals
- No new styling framework (no Tailwind, styled-components).
- No redesign, no theming or light mode (tokens make it possible later).
- No renaming of CSS classes in the same PR as a move (see §5).

## 3. Target architecture

```
apps/dashboard/src/
  styles/
    index.scss            # only file imported by main.tsx; @forwards/@uses the rest
    _tokens.scss          # :root custom properties (colors, fonts, space, radius, type, shadow, z-index)
    _reset.scss           # *, html/body/#root, button font inherit, h1
    _animations.scss      # shared @keyframes + prefers-reduced-motion block
    _mixins.scss          # panel-surface, focus-ring, truncate, responsive breakpoints
  components/<Name>/
    <Name>.module.scss    # scoped styles for that component
    <Name>.tsx
  ui/                     # shared primitives (see below), same colocated layout
```

### Decision: CSS Modules for component-owned styles

- New and moved styles use `Name.module.scss`, imported as `import styles from './Name.module.scss'`. Vite supports this with no config change.
- Class names are scoped, which removes the cross-file collisions in §1.2 and makes dead CSS detectable per file.
- Existing plain `Component.scss` files (~20) are converted opportunistically, in a later phase, not as a prerequisite.
- Add `src/types/scss.d.ts` (`declare module '*.module.scss'`) if `vite/client` types do not already cover it. Check `tsconfig`.
- Tests use class names in places (to be checked per component). Where a test asserts on a global class, switch to role/text queries, or keep a stable `data-testid`. Don't use the hashed name.

### Shared primitives (stay global-looking but become real components)

Replace the misnamed shared classes with small components, each with its own module. This matches the repo's recent direction ("small reusable components", PR #29).

| Today | Used in | Becomes |
| --- | --- | --- |
| `button`, `button--start/--stop/--workflow/--small`, `back-button`, `link-button`, `small-link` | 45 / 8 / 17 / 7 files | `ui/Button` with `variant: 'primary' \| 'secondary' \| 'danger' \| 'workflow' \| 'link'` and `size` props |
| `panel`, `panel-header`, `panel-title`, `eyebrow` | 10 / 6 / 5 / 8 | `ui/Panel` (+ `Panel.Header`) |
| `settings-input`, `settings-desc`, `settings-error`, `settings-form*` | 9 / 23 / 9 / 5 | extend the existing `FormFields/*` (these four already wrap `settings-input`), plus `ui/FieldHint`, `ui/FieldError`, `ui/Form` |
| `settings-table`, `settings-row--inactive`, `settings-actions` | 8 / 1 / 2 | `ui/DataTable` styles (a module consumed by the 5 workflow tables and `ProjectList`) |
| `auth-loading` | 11 | existing `StatusMessage`, extended with a `loading`/`empty` variant |
| `orb`, `neural-bg` | 10 / 2 | `ui/Orb`, `AppShell` background (module) |

A primitive may keep a plain class as an escape hatch only when a parent needs layout control. Prefer a `className` prop merged with a tiny local `cx()` helper (no new dependency).

### Tokens (`_tokens.scss`)

Keep every existing variable name (no churn) and add the missing ones:

- Declare the currently undefined `--font-mono`, `--danger` (value `#ff4444` to match existing fallback), `--text` (`#edfaff`, the current root `color`), `--font-sans`.
- Add scales: `--space-1…8` (4px base), `--radius-sm/md/lg`, `--text-xs/sm/md/lg` (11/12/13/14 and so on), `--z-*`.
- Introduce semantic aliases for the most repeated literals, found by grepping the 232 hard-codes: `--surface-border`, `--surface-glow`, `--status-ok/warn/error` (+ `-soft`).
- Do not try to tokenize every one-off gradient. Only values repeated 3+ times.

## 4. Inline style policy

| Case | Rule |
| --- | --- |
| Static layout/typography/color | Move to the component's module. Never inline |
| Value from props/state selecting between fixed options (e.g. `color` by `project.active`) | Modifier class (`styles.danger`) |
| Truly computed numeric value (`Tooltip` `top/left/visibility`, a width from data) | Keep inline, or set a CSS custom property inline (`style={{ '--x': px }}`) and consume it in SCSS |

Add ESLint `react/forbid-dom-props` for `style`, with an override block (or `eslint-disable-next-line` plus reason) for `Tooltip.tsx`. Enable it in the last phase, after cleanup, so CI stays green throughout. The dashboard has `eslint.config.js`, so extend that.

## 5. Migration plan

Each phase is a separate PR, follows the repo's PR/branch rules (base on `origin/develop`) and must pass `tsc`, `vitest run`, and `eslint` on changed files only (README: do not run Prettier on the whole folder). Each phase includes before/after screenshots (Playwright, same viewport, the views listed in §7) and the visual-diff must be empty.

**Phase 0: Safety net.** Add a Playwright screenshot script or e2e spec (the repo has `e2e/`) that captures the main screens at 1440 px and 800 px (the two media breakpoints in `styles.scss` are 1280 and 820) and stores baselines. No product code changes.

**Phase 1: Tokens, reset, dead code.**
1. Create `styles/` with `_tokens.scss`, `_reset.scss`, `_animations.scss`; move `:root`, `*`, `html/body/#root`, `button`, `h1`, and the `prefers-reduced-motion` block out of `styles.scss`. `main.tsx` imports `styles/index.scss`.
2. Declare the undefined tokens (§3). Remove the per-use fallbacks that are now redundant.
3. Delete the 7 dead classes after re-running the reference check. Merge the duplicate `.primary-link`/`.status-bar` definitions *keeping cascade-equivalent result* (the later rule wins today).

**Phase 2: Move single-owner rules.** For the 24 classes used by one component (list in §1.1 and the script output), create/extend that component's `.module.scss` (or its existing `.scss` if converting is not yet worthwhile), move the rule **and its `@media` fragments** (`styles.scss` has two media blocks at 649 and 680 that touch many classes, so split these by selector). Consolidate `.pr-card` into one definition in `WorkspacePanels`. Order matters: for any selector split across files, preserve the effective cascade.

**Phase 3: Shared primitives.** One PR per primitive, in this order (largest blast radius last): `Panel`, `DataTable`, `FieldHint`/`FieldError`/`Form`, `StatusMessage` loading variant, `Button`. Each PR: create the component + module, replace all usages of the old class (grep guard: zero remaining references), delete the old rule from the global file. Rename in the **same** PR as the move only for these primitives (that is the point), but nowhere else.

**Phase 4: Inline styles.** Remove the 12 static inline styles by moving them into the modules (`ProjectSettings` page header and `main` wrapper first, since it has the most). Convert `ProjectList` color toggles to modifier classes. Keep `Tooltip`'s dynamic style. Turn on the ESLint rule from §4.

**Phase 5: Convert remaining colocated `.scss` to modules.** ~20 files, mechanical, can be batched by folder (`components/`, `workflow/`, `spec/`). Selectors that depend on global classes of other components (`.panel h2` style descendant selectors) are the hazard: replace with local classes.

**Phase 6: Hard-coded colors.** Replace repeated `rgba()` literals with the semantic tokens from §3. Do it per file, with the visual diff as the gate. Optional and incremental.

## 6. Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Cascade changes when rules move (duplicates in §1.2, `app.scss` is imported after `styles.scss` only through import order of `main.tsx` then `App.tsx`) | Phase 0 screenshots; move duplicates in one commit with the merged result; keep source order when concatenating |
| Descendant selectors (`.panel h2`, `.evidence-event strong`, `.pr-card span:not(...)`) break when children get scoped class names | Convert to explicit local classes on the children in the same PR |
| Tests that query by class name | Grep `container.querySelector('.` and `toHaveClass` in `*.test.tsx` per phase; use roles/text |
| Class names built dynamically (`status-chip--${kind}`, `button--${x}`) are invisible to a plain grep | For modules, use `styles[kind]` with a typed union; keep a test that every union member has a class |
| Dead-code detection false positives from dynamic names | Treat §1.3 list as candidates; grep for prefix strings before deleting |
| Large diffs mixing moves and reformatting | Per CLAUDE.md/README: format only changed files; do not reformat moved rules beyond what the move needs |

## 7. Acceptance criteria

- `styles.scss` and `app.scss` no longer exist; `styles/` holds only tokens, reset, animations and mixins (< ~150 lines total excluding the token list).
- `grep -rn "style={{" apps/dashboard/src --include=*.tsx` returns only intentionally dynamic uses (expected: `Tooltip.tsx`), and the ESLint rule enforces it.
- No `var(--x)` reference to an undeclared custom property (add a small script/CI check: collect `var(--…)` names, compare to declared `--…:` names).
- No class defined in more than one SCSS file; no selector without a reference (script check, allowing documented dynamic prefixes).
- Screenshot diff is empty for: project selection, main workspace, project settings, skills hub, workflow dashboard/design/execution, a modal, at both breakpoints.
- `tsc`, `vitest run` and `eslint` (changed files) pass in `apps/dashboard`. Known-flaky timing tests are listed in the contributor notes; rerun once before reporting.

## 8. Decisions

1. **Scoping: CSS Modules.** Component styles use `*.module.scss`.
2. **Button consolidation** happens after Phases 1 and 2 have landed (last of the Phase 3 primitives).
3. **Landing is ignored for now.** `apps/landing` is out of scope.
