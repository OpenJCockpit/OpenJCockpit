# `apps/landing/src/ui/` — the animated/framed UI pattern

This directory replaces the third-party UI/animation framework that used to
be a real runtime dependency of this application. There is no
third-party UI/animation framework here — everything is either authored by
us or, in one clearly marked case, adapted from a third party with full
attribution. This file is the pattern's source of truth: read it before
adding any new animated or framed UI, in this app or in `apps/dashboard`.

## Layout

```
apps/landing/src/ui/
├── README.md                    ← this file
├── motion/
│   ├── Reveal.jsx                entrance animation primitive (CSS-only)
│   ├── RevealGroup.jsx           stagger scope
│   ├── reveal.scss
│   └── usePrefersReducedMotion.js
├── frames/
│   ├── FrameOctagon.jsx          measured-SVG octagonal frame
│   └── frameOctagon.scss
├── backgrounds/
│   ├── PuffsBackground.jsx       canvas 2D ambient particle field
│   └── puffsBackground.scss
└── vendor/
    └── react-bits/
        ├── ATTRIBUTION.md        upstream project, licence (incl. rider), commit, adaptations
        └── DecipherText.jsx      adapted from React Bits' DecryptedText
```

**Stylesheet naming convention:** every stylesheet in this app is
`<lowerCamelComponentName>.scss`. SCSS is the source format for all of them,
imported directly by the matching `.jsx`/`.js` module. These files currently
contain plain CSS only — no nesting, variables, or mixins — the `.scss`
extension exists so Sass features are available later without a second
migration.

`ui/vendor/**` is the **only** third-party-derived code in this tree.
Everything else under `ui/` is code we wrote ourselves, even where it
reproduces the visual behaviour of a former dependency (the frame and the
particle background were reimplemented from documented parameters, not
copied). This split is deliberate and greppable: a source-import audit and
a licence audit both have exactly one directory to look at
(`ui/vendor/**`).

## The animation primitive: CSS keyframes, not a library

Every entrance animation in this app is driven by `<Reveal>` /
`<RevealGroup>`, which are **CSS-only** — no WAAPI, no JS timers, no
`requestAnimationFrame`. `Reveal` sets a handful of CSS custom properties
(`--reveal-duration`, `--reveal-index`, `--reveal-y-from`,
`--reveal-scale-from`) and a `reveal` class bound to one shared
`@keyframes revealIn` (`animation-fill-mode: both`, which prevents a flash
of un-transformed content before the animation starts). `RevealGroup` only
sets `--reveal-stagger` on a wrapper element; **the caller always passes an
explicit `index`, never an auto-incremented one** — an auto-increment
counter would be a render-time side effect and would break under React
StrictMode's double-invocation of render.

```jsx
<Reveal as="button" type="button" duration={1.05} index={1} from={{ y: 34, scale: 0.92 }}>
  ...
</Reveal>
```

All props other than `as`, `duration`, `index`, `from`, `className` and
`style` pass straight through to the rendered element — no extra wrapper
node is introduced, so `type="button"`, `onClick`, `aria-*` etc. all keep
working exactly as if you had written the plain element yourself.

**Trap: don't put a hover/state `transform` directly on a `.reveal`
element.** `reveal.css`'s `animation-fill-mode: both` permanently pins the
element's `transform`/`opacity` at the entrance animation's end-state cascade
origin once the entrance completes, so an ordinary CSS rule like
`.some-class:hover { transform: ... }` declared on the *same* element that
also carries the `reveal` class (e.g. `<Reveal as="button" className="some-class">`)
will not visibly take effect — the animation's fill-mode wins the cascade for
that property on that element. Put hover/state transforms on a child element
instead of the `.reveal` element itself. `PortalOrb`
(`components/PortalOrb/PortalOrb.jsx`) hit exactly this trap on
`.portal-orb:hover { transform: ... }` (QA-reported, since
fixed): the lift transform now lives on a `.portal-orb-inner` child span
wrapping the orb's visual content, while `.portal-orb` itself (which still
carries `reveal`) keeps only the unaffected `filter` hover transition — see
`components/PortalOrb/PortalOrb.scss`'s `.portal-orb-inner` rule for the worked fix.

**Why CSS-only matters beyond simplicity:** it makes the existing global
`@media (prefers-reduced-motion: reduce)` block in `apps/landing/src/styles.scss`
automatically authoritative for every entrance animation for free. You do
not have to remember to add reduced-motion handling to a new `<Reveal>`
usage — it is already covered. Only components that own their *own*
JS or canvas animation (see below) need explicit reduced-motion code.

## The frame primitive: measured SVG, not `clip-path`

`FrameOctagon` renders an absolutely positioned `<svg aria-hidden
role="presentation">` as the first child of its (already `position:
relative`, sized) host, and measures the host with a `ResizeObserver` to
emit exact pixel-coordinate paths — this is what makes the frame visually
identical to the pre-migration output rather than an approximation.
`clip-path` was evaluated and rejected: a two-layer clipped "border" comes
out sub-pixel on the 45° corner cuts, and painting a background directly on
a caller element (instead of on the frame's own fill layer) can expose
dormant CSS on that element that was never meant to be visible.

**Do not give the elements that use `FrameOctagon` (`.auth-card`,
`.topbar`, `.intro-frame` today) their own `border` or `background`.** The
frame SVG owns both the outline and the translucent fill via the two design
tokens below; the host elements only ever declare geometry (size, padding,
`backdrop-filter`). This is a recorded regression trap, not a stylistic
preference — see the requirements/architecture documents under
`docs/delivery/arwes-to-react-bits-migration/` for why.

```jsx
<div className="my-framed-panel" style={{ position: 'relative' }}>
  <FrameOctagon
    duration={1}
    style={{ '--frame-line-color': 'hsl(184 100% 58%)', '--frame-bg-color': 'hsl(230 50% 12% / 58%)' }}
  />
  <div className="my-framed-panel-content">...</div>
</div>
```

### The `--frame-*` token contract

| Custom property | Meaning | Default (in `:root`) |
|---|---|---|
| `--frame-line-color` | Octagon outline colour. State-driven where relevant (e.g. red for an error state, cyan otherwise). | `hsl(184 100% 58%)` |
| `--frame-bg-color` | Octagon translucent fill colour. | `transparent` |

Set both inline on the `<FrameOctagon style={{ ... }}>` element at each
call site; the `:root` defaults are just a safe fallback, not the
per-call-site source of truth.

## Reduced motion: a two-component contract

Because entrance animation is CSS-only, the JS/canvas surface that needs
its **own** explicit reduced-motion handling is exactly two components:
`PuffsBackground` and `DecipherText` (in `ui/vendor/react-bits/`). Both use
the shared `usePrefersReducedMotion()` hook (`ui/motion/usePrefersReducedMotion.js`),
which wraps `matchMedia('(prefers-reduced-motion: reduce)')` and reacts to
live changes. If you add a new JS-driven or canvas/WebGL-driven animation,
it must follow the same contract:

1. Check `usePrefersReducedMotion()` (or the exported
   `getPrefersReducedMotion()` for a one-off, non-reactive read) and render
   the final/static state immediately when it is `true`, never starting a
   timer/rAF loop.
2. Pause on `document.visibilitychange` (cancel the loop when the tab is
   hidden, resume when visible) — this is a performance requirement, not
   just an accessibility one.
3. Be `aria-hidden="true"` (and usually also `role="presentation"`) when
   purely decorative.
4. Tear down every subscription (`cancelAnimationFrame`, `clearInterval`,
   `ResizeObserver.disconnect()`, removed event listeners) on unmount, so
   the component is safe under React StrictMode's double-invocation of
   effects in development.

Prefer CSS-driven animation over JS/canvas whenever the effect allows it —
that's what puts you back under the global reduced-motion block for free
instead of having to implement points 1–4 yourself.

## Copy-on-demand: how `apps/dashboard` adopts this pattern

There is **no physical code sharing** between `apps/landing` and
`apps/dashboard` today (`docker-compose.yml` builds each app from its own
directory as an independent context; there is no root npm workspace). The
model is **canonical-source + copy-on-demand**:

1. `apps/landing/src/ui/` is the canonical implementation and the canonical
   convention.
2. When `apps/dashboard` first needs an animated/framed primitive, **copy
   the file verbatim** into `apps/dashboard/src/ui/<same relative path>`,
   converted to TypeScript and authored/typechecked against **React 18**
   types (the dashboard currently has a `@types/react@^19.2.17` /
   React 18.3 runtime mismatch — do not let a new copy trip `tsc` on this;
   this mismatch is tracked as its own, separate follow-up). Keep the same
   file name (including the `.scss` extension), the same class names, and
   the same CSS custom-property names as the canonical source. As of the
   `dashboard-scss-migration` story, `apps/dashboard` carries
   `sass@^1.104.0` as a build-time `devDependency`, uses `.scss` for
   **all** of its styles (a trimmed global `src/styles.scss` plus one
   co-located sheet per component), and already follows the same
   co-located `<lowerCamelComponentName>.scss` convention as `apps/landing`
   — so a `ui/` primitive now copies in with **no dependency or tooling
   change** on the dashboard side.
3. **Divergence is a defect.** If the dashboard's copy needs to behave
   differently, that is a signal to either extend the canonical primitive
   (and re-copy) or to promote to a shared package (next point) — not to
   quietly fork the behaviour. The file extension is part of the
   verbatim-copy contract: a copy must stay `.scss`.
4. **Promotion trigger.** As soon as a third consumer appears, or a copy
   has genuinely diverged and can't be reconciled, promote `ui/` to a real
   shared package via npm workspaces. That is its own story: it requires a
   root `package.json`, both Dockerfiles rewritten, and both
   `docker-compose.yml` build contexts moved to the repository root — a
   large enough change that it should not be done incidentally.

## Updating the one vendored/adapted component

`ui/vendor/react-bits/DecipherText.jsx` is adapted from React Bits'
`DecryptedText` component. React Bits has no release tags, so the only
stable version handle is a commit SHA, recorded in
`ui/vendor/react-bits/ATTRIBUTION.md` together with the full upstream
licence text (MIT + Commons Clause License Condition v1.0) and an itemised
list of the mandatory local adaptations. To pick up an upstream change:
fetch the file at a new, explicitly chosen commit, diff it against the
recorded SHA, re-apply every adaptation in `ATTRIBUTION.md` by hand (never
auto-merge — the API has deliberately diverged from upstream), update the
recorded SHA and date, and re-run the manual visual/accessibility checklist
for the intro greeting. The non-republication constraint in `CLAUDE.md`
("React and animated UI") applies to this directory: it must not be
copied into `docs-site/` or any other published surface, and it must be
re-reviewed against its licence before any change in this repository's
visibility.

## Adding a new primitive: checklist

- [ ] Does it really need a new npm dependency? Apply the dependency gate
      in `CLAUDE.md` first. If in doubt, don't add one — escalate instead.
- [ ] Is the animation CSS-only where at all possible (`Reveal`/`RevealGroup`)?
- [ ] If it must be JS/canvas-driven, does it satisfy all four points in
      "Reduced motion: a two-component contract" above?
- [ ] Semantic HTML, keyboard operability, visible focus, and sufficient
      contrast preserved?
- [ ] Decorative-only elements marked `aria-hidden`?
- [ ] If copied from `apps/landing` into `apps/dashboard`, is it a
      verbatim copy (same names, same properties), typechecked against
      React 18 types?
- [ ] If adapted from third-party source, does it live under `ui/vendor/**`
      with a full attribution header and an entry in that directory's
      `ATTRIBUTION.md`?
