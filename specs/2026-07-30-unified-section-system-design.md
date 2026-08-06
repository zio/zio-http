# Unified Section System + Glass card-modern — Design Spec

**Date:** 2026-07-30
**Scope:** Give every ZIO HTTP homepage section one consistent title treatment
(`SectionWrapper` + `section-title` + gradient rule) and one consistent card
treatment (glass `card-modern`), ported from the zio.dev revamp and adapted to
the ZIO HTTP green brand and the site's dark homepage surface.
**Branch:** `modern-website` (continues the homepage modernization; PR #4237).
**Reference:** zio.dev `ui/SectionWrapper` + the `.card-modern` / `.section-title`
/ `.eyebrow` / `.gradient-rule` primitives in its `custom.css`.

## Decisions (locked with user, 2026-07-30)
- **Sections:** all six homepage sections.
- **Gradient:** green → teal, `linear-gradient(90deg, #81c784, #26a69a)`.
- **Refactor depth:** minimal and consistent — unify the title shell and convert
  card containers; keep each section's existing layout and content.
- **Card look:** glass (translucent + blurred) on the dark page surface.

## Context / current state
- Only the hero and Key Features got a modern treatment; the rest use ad-hoc
  styling.
- Most section headers use a global `.sectionHeader` (3rem) class. Three sections
  (Ecosystem, Zionomicon, Users) wrote `class="sectionHeader"` instead of
  `className=` — in React that attribute is ignored, so those headers currently
  render **unstyled**. This refactor replaces those headers with `SectionWrapper`
  and fixes the bug as a side effect.
- Tailwind v4 is already available; `SectionWrapper` may use Tailwind utility
  classes plus the new global primitive classes.

## Design

### 1. `SectionWrapper` component

`website/src/components/SectionWrapper/index.js`:

```jsx
import React from 'react';
import clsx from 'clsx';

export default function SectionWrapper({
  eyebrow,
  title,
  subtitle,
  className,
  children,
}) {
  return (
    <section className={clsx('section-modern', className)}>
      {title ? (
        <div className="container section-modern__head">
          {eyebrow ? <span className="eyebrow">{eyebrow}</span> : null}
          <h2 className="section-title">{title}</h2>
          <div className="gradient-rule" />
          {subtitle ? <p className="section-subtitle">{subtitle}</p> : null}
        </div>
      ) : null}
      {children}
    </section>
  );
}
```

- `.section-modern` supplies vertical rhythm (`padding: 3.5rem 0`).
- `.section-modern__head` is the centered header block (flex column, centered,
  `margin-bottom: 2.5rem`).
- `subtitle` renders under the rule in the muted section-subtitle style.

### 2. `custom.css` primitives (appended)

```css
:root {
  --gradient-brand: linear-gradient(90deg, #81c784, #26a69a);
  --brand-shadow: rgba(129, 199, 132, 0.18);
}

/* Section shell + header */
.section-modern { padding: 3.5rem 0; }
.section-modern__head {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  margin-bottom: 2.5rem;
}
.section-title {
  font-size: 2.25rem;
  font-weight: 800;
  letter-spacing: -0.02em;
  line-height: 1.1;
  margin: 0.5rem 0 0;
}
.section-subtitle {
  margin: 1rem 0 0;
  max-width: 42rem;
  color: var(--ifm-color-emphasis-700);
}
.eyebrow {
  display: inline-block;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.09em;
  text-transform: uppercase;
  color: #2e7d32;
  background: rgba(129, 199, 132, 0.16);
  padding: 0.28rem 0.85rem;
  border-radius: 999px;
}
.gradient-text {
  background: var(--gradient-brand);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.gradient-rule {
  width: 48px;
  height: 3px;
  border-radius: 2px;
  background: var(--gradient-brand);
  margin: 0.85rem auto 0;
}

/* Glass card for the dark homepage surface */
.card-modern {
  position: relative;
  overflow: hidden;
  border-radius: 14px;
  /* opaque fallback declared before backdrop-filter */
  background: rgba(30, 32, 48, 0.6);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(8px);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    border-color 0.2s ease;
}
.card-modern::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--gradient-brand);
  opacity: 0;
  transition: opacity 0.2s ease;
}
@media (prefers-reduced-motion: no-preference) {
  .card-modern:hover {
    transform: translateY(-4px);
    box-shadow: 0 14px 30px var(--brand-shadow);
    border-color: rgba(129, 199, 132, 0.4);
  }
  .card-modern:hover::before { opacity: 1; }
}
@media (prefers-reduced-motion: reduce) {
  .card-modern:hover { border-color: rgba(129, 199, 132, 0.4); }
}
```

**Note on the CSS-module caveat:** global `.card-modern` cannot reach a hashed
CSS-module class. Sections whose cards are styled via a module (Ecosystem)
must add the literal global class `card-modern` to the JSX element
(`className={clsx('card-modern', styles.ecosystemCard)}`), not rely on the
module class alone.

### 3. Per-section application (minimal)

| Section | File | Change |
| --- | --- | --- |
| Key Features | `HomepageFeatures/index.js` (+css) | Replace the header markup with `<SectionWrapper title="Key Features" subtitle="…">`; add `card-modern` to each `.featureCard` and remove `.featureCard`'s own `background`/`border`/`box-shadow` (card-modern supplies them). Keep the `border-top` accent? No — card-modern's `::before` replaces it; drop the `border-top`. |
| Ecosystem | `HomepageEcosystem/index.js` (+css) | Wrap in `SectionWrapper title="Ecosystem"`; `className={clsx('card-modern', styles.ecosystemCard)}` on each card; strip the module card's own bg/border; fix `class=`→`className`. |
| Zionomicon | `HomepageZionomicon/index.js` (+css) | Wrap in `SectionWrapper title="Learn ZIO HTTP with Zionomicon"`; book-cover frame → `card-modern`; fix `class=`. |
| Who's Using | `HomepageUsers/index.js` (+css) | Wrap in `SectionWrapper title="Who is Using ZIO HTTP?"`; adopter logo tiles → `card-modern`; fix `class=`. |
| Showcases | `HomepageShowcases/index.js` (+css) | Apply `.section-title` to its "Imperative API" / "Declarative API" headings; wrap each code-example container in `card-modern`. No centered SectionWrapper (side-by-side layout). |
| In Action | `HomepageCodeSnippet/index.js` (+css) | Two-column layout stays; restyle its `<h2>` to `.section-title`. The existing dark tabbed panel already reads as a card — leave it (do NOT double-wrap). |

- Remove now-unused `.sectionHeader` usages as each section migrates; delete the
  `.sectionHeader` rule from `custom.css` once no section references it.
- Do not change any copy or restructure layouts beyond swapping the header shell
  and card containers.

## Verification
- `yarn build` (or webpack compile) succeeds with no errors.
- Browser check on the dark homepage: every section shows the same centered
  title + green→teal rule; all cards are glass with a consistent hover
  (lift + gradient top-bar + green border); the three previously-broken headers
  (Ecosystem, Zionomicon, Users) now render styled.
- No horizontal overflow; cards legible on the dark surface; reduced-motion shows
  the static hover accent only.

## Out of scope
- Ambient background glow, count-up stats, font upgrade, docs-page styling
  (separate follow-ups from the modernization brainstorm).
- Any content/copy changes or section reordering.

## Risks
- `backdrop-filter` support: opaque fallback background is declared first.
- Ecosystem module-class reach: handled by adding the global `card-modern` class
  in JSX (see note above).
- Removing `.featureCard`/`.ecosystemCard` backgrounds must not leave a
  double-border with `card-modern`; strip the old surface properties in the same
  edit.
