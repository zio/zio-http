# Unified Section System + Glass card-modern Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give all six ZIO HTTP homepage sections one consistent title treatment (`SectionWrapper` + `section-title` + green→teal gradient rule) and one glass `card-modern` treatment, on the dark homepage surface.

**Architecture:** Add one shared `SectionWrapper` component and a set of global CSS primitives to `custom.css` (Task 1), then migrate each section to use them (Tasks 2–7). Sections are independent; each touches only its own `index.js` + `styles.module.css`.

**Tech Stack:** Docusaurus 3.10, React 18 (JSX), Tailwind v4 (available), `clsx` (available).

**Spec:** `specs/2026-07-30-unified-section-system-design.md`

## Global Constraints

- **Yarn only** — never `npm`, never commit `package-lock.json`. Commands run from `website/`.
- **No unit-test harness** — verification is `yarn build` / webpack compile success + SSR-HTML grep + (controller) browser check. Do NOT write Jest/Vitest tests. `yarn build` alone fails on the pre-existing missing-mdoc `concepts/*` docs — NOT a regression; confirm webpack compiles the changed files with no errors and grep the built HTML.
- **Brand gradient:** `--gradient-brand: linear-gradient(90deg, #81c784, #26a69a)` (green→teal). Never red.
- **Dark surface:** the homepage sections render on a dark background; `card-modern` is a glass style (translucent + blur) with an opaque fallback declared first.
- **CSS-module caveat:** global `.card-modern` cannot reach a hashed CSS-module class. Where a card is styled via a module class, add the literal global class in JSX: `className={clsx('card-modern', styles.theCard)}`.
- Commits: Conventional Commits; end each message with `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.
- Work on branch `modern-website`. Specs/plans live in repo-root `specs/`, never in `docs/`.

---

## File Structure

New:
- `website/src/components/SectionWrapper/index.js`

Modified:
- `website/src/css/custom.css` (add primitives; later remove `.sectionHeader`)
- `website/src/components/HomepageFeatures/index.js` + `styles.module.css`
- `website/src/components/HomepageEcosystem/index.js` + `styles.module.css`
- `website/src/components/HomepageZionomicon/index.js` + `styles.module.css`
- `website/src/components/HomepageUsers/index.js` + `styles.module.css`
- `website/src/components/HomepageShowcases/index.js` + `styles.module.css`
- `website/src/components/HomepageCodeSnippet/index.js`

---

### Task 1: Primitives — SectionWrapper + custom.css

**Files:**
- Create: `website/src/components/SectionWrapper/index.js`
- Modify: `website/src/css/custom.css` (append primitives)

**Interfaces:**
- Produces: default export `SectionWrapper({ eyebrow?, title?, subtitle?, className?, children })`; global classes `.section-modern`, `.section-modern__head`, `.section-title`, `.section-subtitle`, `.eyebrow`, `.gradient-text`, `.gradient-rule`, `.card-modern`. Consumed by Tasks 2–7.

- [ ] **Step 1: Create the component**

Create `website/src/components/SectionWrapper/index.js`:

```jsx
import React from 'react';
import clsx from 'clsx';

// Shared section shell: centered eyebrow/title/gradient-rule/subtitle header,
// then the section's own content as children.
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

- [ ] **Step 2: Append the primitives to custom.css**

Append to the end of `website/src/css/custom.css`:

```css
/* ── Section system + modern card primitives ─────────────────────────── */
:root {
  --gradient-brand: linear-gradient(90deg, #81c784, #26a69a);
  --brand-shadow: rgba(129, 199, 132, 0.18);
}

.section-modern {
  padding: 3.5rem 0;
}

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
  background: rgba(30, 32, 48, 0.6); /* opaque fallback (before blur) */
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
  .card-modern:hover::before {
    opacity: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .card-modern:hover {
    border-color: rgba(129, 199, 132, 0.4);
  }
}
```

- [ ] **Step 3: Build**

From `website/`: `yarn build` (mdoc-gated `concepts/*` failure is fine). Confirm webpack compiles with no CSS/JS errors.

- [ ] **Step 4: Commit**

```bash
git add website/src/components/SectionWrapper/index.js website/src/css/custom.css
git commit -m "feat(website): add SectionWrapper + glass card-modern primitives

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Key Features → SectionWrapper + card-modern

**Files:**
- Modify: `website/src/components/HomepageFeatures/index.js`
- Modify: `website/src/components/HomepageFeatures/styles.module.css`

**Interfaces:** Consumes `SectionWrapper` + `.card-modern` from Task 1.

- [ ] **Step 1: Use SectionWrapper for the header**

In `HomepageFeatures/index.js`, add the import:

```js
import SectionWrapper from '@site/src/components/SectionWrapper';
```

Replace the `export default function HomepageFeatures()` return body. The current markup wraps everything in `<section className={styles.features}>` with a `sectionHeader` h2 + subtitle, then a `.featureCards` row. Change it to:

```jsx
export default function HomepageFeatures() {
  return (
    <SectionWrapper
      title="Key Features"
      subtitle="Build high-performance, scalable web applications with ZIO HTTP"
      className={styles.features}
    >
      <div className={styles.wideContainer}>
        <div className={clsx('row', styles.featureCards)}>
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </SectionWrapper>
  );
}
```

(The old `.featuresHeader` row + `<h2 className="sectionHeader">` + `.featuresSubtitle` block is removed — SectionWrapper renders the title/subtitle.)

- [ ] **Step 2: Make each card a card-modern**

In the `Feature(...)` component, change the card wrapper to include the global class:

```jsx
<div className={clsx('card-modern', styles.featureCard)}>
```

- [ ] **Step 3: Strip the old card surface**

In `HomepageFeatures/styles.module.css`, in the `.featureCard` rule, remove the properties now supplied by `card-modern`: `background-color`, `border`, `border-top`, `box-shadow`, `border-radius`. Keep layout properties (`height`, `display`, `flex-direction`, `padding`). Leave `.featureCardIcon` / `.featureCardHeader` / `.featureCardDescription` rules unchanged.

- [ ] **Step 4: Build + verify**

From `website/`: `yarn build`. Then:

```bash
grep -c "section-title" build/index.html   # expected: >=1
grep -c "card-modern" build/index.html      # expected: >=1
```

- [ ] **Step 5: Commit**

```bash
git add website/src/components/HomepageFeatures/index.js website/src/components/HomepageFeatures/styles.module.css
git commit -m "style(website): unify Key Features onto SectionWrapper + card-modern

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: Ecosystem → SectionWrapper + card-modern

**Files:**
- Modify: `website/src/components/HomepageEcosystem/index.js`
- Modify: `website/src/components/HomepageEcosystem/styles.module.css`

**Interfaces:** Consumes Task 1 primitives.

- [ ] **Step 1: Header via SectionWrapper**

Add import `import SectionWrapper from '@site/src/components/SectionWrapper';`

Replace the outer `<section className={styles.ecosystem}>` + the `.ecosystemHeader` row (which contains the buggy `<h2 class="sectionHeader">Ecosystem</h2>` + `.ecosystemSubtitle`) so the return is:

```jsx
  return (
    <SectionWrapper
      title="Ecosystem"
      subtitle="A complete toolkit for building scalable, resilient applications"
      className={styles.ecosystem}
    >
      <div className={styles.wideContainer}>
        {/* ZIO in its own row */}
        <div className={clsx('row', styles.ecosystemCards)}>
          ... (unchanged card rows below) ...
        </div>
        ...
      </div>
    </SectionWrapper>
  );
```

Keep the two card-row blocks exactly as they are, except Step 2.

- [ ] **Step 2: card-modern on both card variants**

Both card wrappers currently read `<div className={styles.ecosystemCard}>` (the main ZIO card at line ~143 and the mapped cards at line ~181). Change **both** to:

```jsx
<div className={clsx('card-modern', styles.ecosystemCard)}>
```

- [ ] **Step 3: Strip old card surface**

In `HomepageEcosystem/styles.module.css`, in `.ecosystemCard`, remove `background`/`background-color`, `border`, `box-shadow`, `border-radius` (keep padding/layout). If a `.ecosystemCard:hover` rule sets background/border/transform/shadow, remove it (card-modern owns hover).

- [ ] **Step 4: Build + verify**

`yarn build`; confirm webpack compiles. Then `grep -c "Ecosystem" build/index.html` ≥ 1 and confirm no `class="sectionHeader"` remains for this section (it is replaced by SectionWrapper).

- [ ] **Step 5: Commit**

```bash
git add website/src/components/HomepageEcosystem/index.js website/src/components/HomepageEcosystem/styles.module.css
git commit -m "style(website): unify Ecosystem onto SectionWrapper + card-modern

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: Zionomicon → SectionWrapper + card-modern

**Files:**
- Modify: `website/src/components/HomepageZionomicon/index.js`
- Modify: `website/src/components/HomepageZionomicon/styles.module.css`

**Interfaces:** Consumes Task 1 primitives.

The current layout is a two-column row (text left, book image right) inside `<section className={styles.zionomicon}>`, with a buggy `<h2 class="sectionHeader">` inside the left column.

- [ ] **Step 1: Wrap in SectionWrapper without a centered header**

Add import. Because the title belongs inside the left column (not a centered section header), do NOT pass `title` to SectionWrapper here — use it only as the section shell:

Change `<section className={styles.zionomicon}>` to `<SectionWrapper className={styles.zionomicon}>` and its closing `</section>` to `</SectionWrapper>`. Then change the in-column heading from the buggy `<h2 class="sectionHeader">Learn ZIO HTTP with Zionomicon</h2>` to:

```jsx
<h2 className="section-title">Learn ZIO HTTP with Zionomicon</h2>
```

- [ ] **Step 2: Book cover as a card-modern frame**

Change the image container `<div className={styles.ziconImageContainer}>` to:

```jsx
<div className={clsx('card-modern', styles.ziconImageContainer)}>
```

Add `import clsx from 'clsx';` if not already imported (it is imported at the top).

- [ ] **Step 3: Strip old frame surface**

In `HomepageZionomicon/styles.module.css`, in `.ziconImageContainer`, remove any `background`/`border`/`box-shadow`/`border-radius` (card-modern supplies them); keep sizing/padding/flex. Ensure the `.ziconImage` still fits (keep its `border-radius` if it has one, or let overflow:hidden on card-modern clip it).

- [ ] **Step 4: Build + verify**

`yarn build`; `grep -c "Learn ZIO HTTP with Zionomicon" build/index.html` ≥ 1.

- [ ] **Step 5: Commit**

```bash
git add website/src/components/HomepageZionomicon/index.js website/src/components/HomepageZionomicon/styles.module.css
git commit -m "style(website): unify Zionomicon section onto the section system

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: Who's Using → SectionWrapper + card-modern

**Files:**
- Modify: `website/src/components/HomepageUsers/index.js`
- Modify: `website/src/components/HomepageUsers/styles.module.css`

**Interfaces:** Consumes Task 1 primitives.

- [ ] **Step 1: Header via SectionWrapper**

Add imports:

```js
import clsx from 'clsx';
import SectionWrapper from '@site/src/components/SectionWrapper';
```

Replace `<section className={styles.users}>` … first `.row` (containing the buggy `<h2 class="sectionHeader">Who are Using ZIO HTTP?</h2>` + `.subtitle`) so the return uses:

```jsx
    <SectionWrapper
      title="Who is Using ZIO HTTP?"
      subtitle="Organizations and projects building with ZIO HTTP in production"
      className={styles.users}
    >
      <div className={styles.wideContainer}>
        <div className={styles.usersGrid}>
          ... (unchanged user items) ...
        </div>
        <div className="row">
          ... (unchanged join-community paragraph) ...
        </div>
      </div>
    </SectionWrapper>
```

Close with `</SectionWrapper>` instead of `</section>`.

- [ ] **Step 2: Each adopter tile as card-modern**

Change the adopter link `className={styles.userItem}` to:

```jsx
className={clsx('card-modern', styles.userItem)}
```

- [ ] **Step 3: Strip old tile surface**

In `HomepageUsers/styles.module.css`, in `.userItem`, remove `background`/`border`/`box-shadow`/`border-radius` and any `.userItem:hover` surface rule (card-modern owns them); keep padding/flex/width.

- [ ] **Step 4: Build + verify**

`yarn build`; `grep -c "Who is Using ZIO HTTP" build/index.html` ≥ 1.

- [ ] **Step 5: Commit**

```bash
git add website/src/components/HomepageUsers/index.js website/src/components/HomepageUsers/styles.module.css
git commit -m "style(website): unify Who's-Using section onto the section system

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 6: Showcases → section-title + card-modern

**Files:**
- Modify: `website/src/components/HomepageShowcases/index.js`
- Modify: `website/src/components/HomepageShowcases/styles.module.css`

The Showcases section has two centered `<h2 className="text--center">` ("Imperative API", "Declarative API") and code examples in `col` columns. It keeps its side-by-side layout (no centered SectionWrapper).

- [ ] **Step 1: section-title on the headings**

Change both `<h2 className="text--center">` to `<h2 className="section-title text--center">` (Imperative API at line ~9, Declarative API at line ~54).

- [ ] **Step 2: Wrap each code example in a card-modern**

Add `import clsx from 'clsx';`. Wrap each `<CodeBlock>` example in a `card-modern` container. For the three examples (Server-side, Client-side, Declarative), wrap the CodeBlock:

```jsx
<div className={clsx('card-modern', styles.showcaseCard)}>
  <CodeBlock language="scala">{`...`}</CodeBlock>
</div>
```

- [ ] **Step 3: Add the wrapper style**

In `HomepageShowcases/styles.module.css`, add:

```css
.showcaseCard {
  padding: 0.5rem;
}
.showcaseCard :global(pre) {
  margin: 0;
}
```

- [ ] **Step 4: Build + verify**

`yarn build`; `grep -c "Imperative API" build/index.html` ≥ 1.

- [ ] **Step 5: Commit**

```bash
git add website/src/components/HomepageShowcases/index.js website/src/components/HomepageShowcases/styles.module.css
git commit -m "style(website): apply section-title + card-modern to Showcases

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 7: In-Action heading + remove legacy .sectionHeader

**Files:**
- Modify: `website/src/components/HomepageCodeSnippet/index.js`
- Modify: `website/src/css/custom.css`

The "ZIO HTTP in Action" section is a two-column layout (text + tabbed code panel). Keep the layout; only restyle its heading. Then remove the now-unused legacy `.sectionHeader` rule.

- [ ] **Step 1: section-title on the heading**

In `HomepageCodeSnippet/index.js`, change `<h2>ZIO HTTP in Action</h2>` to `<h2 className="section-title">ZIO HTTP in Action</h2>`.

- [ ] **Step 2: Confirm no remaining .sectionHeader users**

Run: `grep -rn "sectionHeader" website/src`
Expected: no matches (all migrated). If any remain, migrate them to `section-title` before continuing.

- [ ] **Step 3: Remove the legacy rule**

In `website/src/css/custom.css`, delete the `.sectionHeader { … }` rule block (font-size 3rem etc.) now that nothing references it.

- [ ] **Step 4: Build + verify**

`yarn build`; confirm compile. `grep -c "ZIO HTTP in Action" build/index.html` ≥ 1; `grep -c "sectionHeader" build/index.html` should be 0.

- [ ] **Step 5: Commit**

```bash
git add website/src/components/HomepageCodeSnippet/index.js website/src/css/custom.css
git commit -m "style(website): section-title on In-Action heading; drop legacy .sectionHeader

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage:** SectionWrapper + primitives → Task 1. Features → 2. Ecosystem → 3. Zionomicon → 4. Users → 5. Showcases → 6. CodeSnippet + `.sectionHeader` cleanup → 7. Green→teal gradient, glass card, module-class caveat, `class=`→`className` fixes all covered. ✓

**Placeholder scan:** Task 1 has full component + CSS. Tasks 2–7 give exact class names, imports, and the "strip these surface properties, keep layout" instruction per file — no TBD/TODO. ✓

**Type/name consistency:** `SectionWrapper` default export + prop names (`eyebrow/title/subtitle/className/children`) consistent across tasks; `card-modern` global class name consistent; every section imports from `@site/src/components/SectionWrapper`. ✓

**Ordering:** Task 1 (primitives) precedes all; `.sectionHeader` removal is last (Task 7) after every section stops using it. ✓
