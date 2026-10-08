# Attribution — `ui/vendor/react-bits`

This directory contains source code **adapted from** a third-party project. It is
handwritten production code we own and review (see "Ownership and adaptation" below),
not a byte-identical vendored copy — but its origin, licence and licence rider are
recorded here in full because BR-7 requires it and because CLAUDE.md's non-republication
constraint (see below) depends on this file being complete and accurate.

## Upstream project

- **Project:** React Bits
- **Repository:** https://github.com/DavidHDev/react-bits
- **Copyright:** © 2026 David Haz
- **Source file copied:** `src/content/TextAnimations/DecryptedText/DecryptedText.jsx`
  (the architecture document's shorthand `TextAnimations/DecryptedText/DecryptedText.jsx`
  refers to this same file; the upstream repo layout has it under `src/content/`)
- **Upstream commit SHA:** `7a7cf3746f944e14c9ae9b7e2ab899df4ee55c77`
  (2026-04-01, "fix: cancel in-flight animation on mouseleave in DecryptedText")
- **No upstream release tags exist.** The commit SHA above is the only stable version
  handle for this component. Any future re-comparison against upstream must be done
  against this SHA (or a newer SHA explicitly chosen and recorded here), never against
  `main`.
- **Local copy taken into this repository on:** 2026-09-07, fetched directly from
  `https://raw.githubusercontent.com/DavidHDev/react-bits/7a7cf3746f944e14c9ae9b7e2ab899df4ee55c77/src/content/TextAnimations/DecryptedText/DecryptedText.jsx`
  and reviewed line by line before adaptation, per the integrity guard in the work plan
  (do not describe a copy-and-adapt that was not actually performed).

## Licence — full verbatim text

Fetched from `https://raw.githubusercontent.com/DavidHDev/react-bits/main/LICENSE.md`.
Reproduced here in full, unparaphrased, including the Commons Clause rider:

```
MIT + Commons Clause License Condition v1.0

Copyright (c) 2026 David Haz

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, and distribute the Software **as part of an application, website, or product**, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

## Commons Clause Restriction

You may use this Software, including for any commercial purpose, **so long as you do not sell, sublicense, or redistribute the components themselves-whether alone, in a bundle, or as a ported version.**

## No Warranty

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## O-1 decision (recorded; revisited on going public)

The MIT + Commons Clause rider was verified during architecture (§2.7/§6.3) and was
**not** what Decision 1 originally assumed (no rider). The O-1 acceptance
(architecture §14a) was given when this was a private commercial product.

This repository is now **public**: https://github.com/OpenJCockpit/OpenJCockpit
(Apache-2.0, see the root `LICENSE` and `NOTICE`). The change in visibility is the
trigger the earlier non-republication constraint required a re-review for.

The Commons Clause permits use and distribution *as part of an application, website, or
product*, including commercially, and forbids selling, sublicensing or redistributing
the components themselves — alone, bundled, or ported. Only one small, materially
adapted component (`DecipherText.jsx`) is used, as part of this application. Publishing
its source in a public repository is nonetheless a form of redistribution, and the
Apache-2.0 licence of the rest of the repository does **not** apply to this directory:
it remains under the upstream MIT + Commons Clause terms reproduced above.

**Constraints (binding):**
- Files under `apps/landing/src/ui/vendor/**` stay under the upstream licence above,
  not Apache-2.0; the root `NOTICE` records this.
- They must not be extracted into a separately distributed package, published
  standalone, or copied into `docs-site/` or any other surface outside this application.
- If the licence review concludes that public distribution of the adapted file is not
  acceptable, replace `DecipherText.jsx` with an original implementation and remove
  this directory.

## Ownership and adaptation (ADR-6)

Per architecture ADR-6, a file under `ui/vendor/**` that we materially modify becomes
**our code** for review, test and coverage purposes. `DecipherText.jsx` in this
directory is a **materially adapted** derivative of the upstream file, not a
byte-identical copy — it therefore does **not** qualify for any coverage exclusion,
and none is added anywhere in this change set (CLAUDE.md records this explicitly, see
the "React and animated UI" section).

### Local adaptations (all mandatory, applied relative to the upstream commit above)

1. **Dropped the `motion/react` import entirely.** Upstream imports `{ motion }` and
   renders `<motion.span>`, but uses it only as a plain wrapper element — no animation
   props are passed to it anywhere in the upstream file. Our `DecipherText` renders a
   plain `<span>` instead. This keeps ADR-2 (zero new npm runtime dependencies) intact;
   pulling in `motion` here would have added a new dependency for zero behavioural gain.
2. **Replaced `speed`/`maxIterations` with an explicit `durationMs` prop.** The reveal
   is now contractually bound to a fixed wall-clock window (1200ms at the `IntroGreeting`
   call site) instead of an iteration count at a fixed interval, so it cannot drift past
   the 2.1s `introExit` CSS delay or the 2850ms `onDone` timer (R-6).
3. **Fixed the upstream screen-reader leak (upstream line 369).** Upstream's sr-only
   span renders `{displayText}`, which is the **currently scrambled** string while the
   animation runs. Our sr-only span always renders the **final** `text` prop, never a
   partially scrambled value — the scrambling characters are confined to a sibling
   `aria-hidden="true"` span. This is required by AC-11 and the privacy NFR ("Decipher/
   scramble effects must not leak the target string to assistive technology in a
   partially scrambled form").
4. **Added explicit `prefers-reduced-motion: reduce` handling.** Upstream has none. Our
   version checks `matchMedia('(prefers-reduced-motion: reduce)')` on mount and renders
   the final text immediately without starting the scramble interval when the user has
   requested reduced motion (BR-9, AC-11).
5. **Reserved layout for the final text (replicating Arwes' `Text manager="decipher"
   fixed` behaviour).** The final text is rendered in a hidden, space-occupying node
   while the visible scrambling layer is absolutely positioned over it, so the reveal
   causes no layout shift (AC-12), independent of whether the substitution characters
   happen to be the same width as the final text.
6. **Character set changed to Arwes' set**, transcribed directly from Arwes'
   text package (`animateTextDecipher/animateTextDecipher.js`, read out of
   the then-installed `node_modules` tree) rather than trusting the
   architecture document's quotation of it:
   `'    ----____abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'`
   (four spaces, four hyphens, four underscores, then lowercase, uppercase, digits).
   This preserves the pre-migration visual character of the reveal (BR-2), rather than
   using React Bits' own default `!@#$%^&*()_+`-flavoured character set.
7. **Full interval teardown** on unmount and whenever the input `text` changes, removing
   upstream's hover/click/view-trigger machinery (`animateOn`, `sequential`,
   `revealDirection`, `IntersectionObserver`, etc.) that `IntroGreeting` does not need —
   our call site always auto-runs the reveal once, on mount, exactly like the Arwes
   `Text manager="decipher"` component did.

### Update procedure

React Bits has no release tags, so there is no "bump the version" step. To pick up an
upstream change:

1. Fetch the file at a **new, explicitly chosen** commit SHA from
   `https://github.com/DavidHDev/react-bits`.
2. Diff it against the SHA recorded above.
3. Re-apply adaptations 1–7 above by hand against the new upstream code — never
   auto-merge/auto-pull, since our copy has diverged from upstream's API by design
   (`durationMs` instead of `speed`/`maxIterations`, no `animateOn`/`sequential`/
   `revealDirection`/click/hover/view props).
4. Update the recorded commit SHA and the date in this file.
5. Re-run the manual visual/accessibility checklist for the intro greeting (decipher
   reveal timing, sr-only content, reduced-motion path).
