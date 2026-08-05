# Agent Onboard for ziohttp.com — Design Spec

**Date:** 2026-07-29
**Scope:** Port the zio.dev "Onboard your agent" feature to the ZIO HTTP website
(`ziohttp.com`): a hero button + compact navbar button that copy a setup prompt to
the clipboard, plus a static `start.md` guide the prompt tells the agent to fetch.
Replaces the existing `HomepageCodingAgent` install-command section. Establishes a
Tailwind v4 foundation on the website as reusable groundwork for the broader
modernization.
**Reference implementation:** zio.dev commits `e8085e70`, `91aba10a`, `26f83142`.

## Goal

Give ZIO HTTP users a one-click way to onboard their AI coding agent. Clicking the
button copies a prompt that points the agent at `https://ziohttp.com/start.md`; the
agent fetches that guide and installs the ZIO skills so it answers from live
documentation instead of stale training data.

This mirrors the flow already shipped on zio.dev, adapted to ZIO HTTP branding
(green primary, not red), the `ziohttp.com` domain, and the ZIO HTTP skill set.

## Decisions (locked with user, 2026-07-29)

- **Styling:** add Tailwind v4 to the website now (the button/logos are authored in
  Tailwind utilities; this also seeds the larger modernization).
- **Old section:** remove `HomepageCodingAgent`; the button + `start.md` become the
  single onboarding path (parity with zio.dev).
- **Navbar button:** include the compact terminal-icon variant via a
  `Navbar/ColorModeToggle` swizzle.
- **`start.md` skill scope:** `zio-http-knowledge` required, `zio-knowledge`
  recommended (HTTP code depends on core ZIO); the Claude Code plugin path installs
  both.
- **Component location:** flat `src/components/OnboardAgentButton/`, matching the
  website's existing `Homepage*` convention rather than zio.dev's `ui/` split.

## Design

### A. Tailwind v4 foundation

The website (`website/`) currently has no Tailwind. Wire in Tailwind v4 the same way
zio.dev does.

**`website/package.json`** — add to `dependencies`:

```json
"@tailwindcss/postcss": "4.3.3"
```

**`website/docusaurus.config.js`** — add an inline plugin at the head of the existing
`plugins: [ ... ]` array:

```js
async function tailwindPlugin(context, options) {
  return {
    name: 'docusaurus-tailwindcss',
    configurePostCss(postcssOptions) {
      postcssOptions.plugins.push(require('@tailwindcss/postcss'));
      return postcssOptions;
    },
  };
},
```

**`website/postcss.config.js`** — new file (matches zio.dev; harmless alongside the
Docusaurus plugin):

```js
module.exports = {
  plugins: [require('@tailwindcss/postcss')],
};
```

**`website/tailwind.config.js`** — new file. Brand primary is ZIO HTTP **green**
(current `--ifm-color-primary: #81c784`), so `primary` maps to Tailwind's green scale:

```js
const colors = require('tailwindcss/colors');

/** @type {import('tailwindcss').Config} */
module.exports = {
  important: true,
  content: ['./src/**/*.{js,jsx,ts,tsx}', './docs/**/*.mdx'],
  darkMode: ['class', '[data-theme="dark"]'],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: colors.green[600],
          ...colors.green,
        },
      },
    },
  },
  plugins: [],
};
```

**`website/src/css/custom.css`** — prepend, **above** the existing Infima
`:root` overrides, the Tailwind layer imports + `@config` + the v4 border-compat
base layer (verbatim from zio.dev):

```css
@layer theme, base, components, utilities;
@import 'tailwindcss/theme.css' layer(theme);
@import 'tailwindcss/utilities.css' layer(utilities);

@config '../../tailwind.config.js';

@layer base {
  *,
  ::after,
  ::before,
  ::backdrop,
  ::file-selector-button {
    border-color: var(--color-gray-200, currentColor);
  }
}
```

**Collision risk:** Tailwind's reset vs Infima. The `@layer theme, base, components,
utilities;` ordering plus `important: true` (utilities win) is what keeps zio.dev
stable — replicate exactly. The border-compat block restores Tailwind v3 default
border color so existing Infima-bordered elements don't change.

### B. OnboardAgentButton component

New directory `website/src/components/OnboardAgentButton/`.

**`index.jsx`** — the hero pill button. Adapted from zio.dev
`ui/OnboardAgentButton/index.jsx`:

- Exports `PROMPT` and `copyPrompt(text)` (reused by the navbar variant).
- `PROMPT = 'Fetch https://ziohttp.com/start.md and follow the instructions to set up my environment for ZIO HTTP development.'`
- `copyPrompt`: `navigator.clipboard.writeText` in secure contexts, legacy
  `document.execCommand('copy')` textarea fallback, returns boolean.
- On click: copy; on failure show `window.prompt(...)` with the text; on success set
  `copied` true for 2s.
- Renders label (`Onboard your agent to ZIO HTTP` / `Copied!`) + the four logos +
  an `sr-only` "Works with Claude, Codex, Cursor, and OpenCode".
- Tailwind classes as on zio.dev; `hover:border-primary hover:text-primary` now
  resolves to green.

**`logos.jsx`** — `ClaudeLogo`, `CodexLogo`, `CursorLogo`, `OpenCodeLogo` SVGs,
copied verbatim from zio.dev (`h-5 w-5`/`h-4.5` Tailwind sizing, `aria-hidden`).

**`NavbarButton.jsx`** + **`NavbarButton.module.css`** — compact terminal-icon
variant, copied from zio.dev. Imports `PROMPT`/`copyPrompt` from `./index`, uses
`react-icons/fa6` `FaTerminal`/`FaCheck` (already a website dependency). CSS module
uses `--ifm-navbar-link-color` and `--ifm-color-primary` (green) on hover — no
Tailwind, so it is theme-correct without extra work.

### C. Wiring

**`website/src/theme/Navbar/ColorModeToggle/index.js`** — new swizzle. Wraps
`@theme-original/Navbar/ColorModeToggle` and renders
`<OnboardAgentNavbarButton />` immediately before it (button lands between the
GitHub navbar link and the theme toggle):

```jsx
import React from 'react';
import ColorModeToggle from '@theme-original/Navbar/ColorModeToggle';
import OnboardAgentNavbarButton from '@site/src/components/OnboardAgentButton/NavbarButton';

export default function ColorModeToggleWrapper(props) {
  return (
    <>
      <OnboardAgentNavbarButton />
      <ColorModeToggle {...props} />
    </>
  );
}
```

**`website/src/components/HomepageHero/index.js`** — import `OnboardAgentButton` and
mount it inside the existing `.buttons` container, next to the "Get Started" link.

- **Contrast risk:** the ZIO HTTP hero is a dark image banner (zio.dev's hero is
  light). The default button uses zinc borders/text that may be low-contrast on the
  dark overlay. Add a hero-scoped style (e.g. a class on the hero button wrapper, or
  a `.heroBanner` descendant rule in the Hero's `styles.module.css`) forcing
  light border/text so the pill reads on the dark hero. Confirm in the browser check.

### D. start.md

**`website/static/start.md`** — served at `https://ziohttp.com/start.md`. ZIO
HTTP-flavored adaptation of zio.dev's `start.md`:

```markdown
# Get your agent ready for ZIO HTTP

Official guide for setting up an AI coding agent to build with
[ZIO HTTP](https://ziohttp.com), the purely functional, type-safe library for
building scalable, correct, and efficient HTTP clients and servers in Scala.

Install the ZIO skills below so your agent answers from live, accurate ZIO HTTP
documentation instead of stale training data.

## Claude Code

Install the plugin — it bundles both the ZIO Knowledge and ZIO HTTP Knowledge
skills:

    claude plugin marketplace add zio/zio-skills
    claude plugin install zio-skills@ziogenetics

Then reload plugins with the `/reload-plugins` command to activate the skills.

## Other agents (Codex, OpenCode, Cursor, etc.)

Install the skills with the `skills` CLI.

ZIO HTTP Knowledge (required):

    npx skills add zio/zio-skills --skill zio-http-knowledge

ZIO Knowledge (recommended — ZIO HTTP builds on core ZIO):

    npx skills add zio/zio-skills --skill zio-knowledge

## Fallback

If you cannot install the skills, fetch the documentation index yourself and use it
to find the right pages:

- https://ziohttp.com/llms.txt — ZIO HTTP
- https://zio.dev/llms.txt — ZIO
```

(Code fences rendered as indented blocks above to avoid nested-fence ambiguity in
this spec; the actual `start.md` uses standard triple-backtick fences.)

Static files under `website/static/` are copied to the site root by Docusaurus, so
no config change is needed for `/start.md` to resolve.

### E. Remove old section

- Delete `website/src/components/HomepageCodingAgent/` (both `index.js` and
  `styles.module.css`).
- In `website/src/pages/index.js`: remove the `HomepageCodingAgent` import and its
  `<HomepageCodingAgent />` mount from `<main>`.

## Files touched

| File | Change |
| --- | --- |
| `website/package.json` | add `@tailwindcss/postcss` dependency |
| `website/docusaurus.config.js` | add `docusaurus-tailwindcss` plugin |
| `website/postcss.config.js` | new — PostCSS Tailwind plugin |
| `website/tailwind.config.js` | new — content globs, dark-mode hook, green `primary` |
| `website/src/css/custom.css` | prepend Tailwind imports + `@config` + border-compat base |
| `website/src/components/OnboardAgentButton/index.jsx` | new — hero button, `PROMPT`, `copyPrompt` |
| `website/src/components/OnboardAgentButton/logos.jsx` | new — four agent SVG logos |
| `website/src/components/OnboardAgentButton/NavbarButton.jsx` | new — compact navbar variant |
| `website/src/components/OnboardAgentButton/NavbarButton.module.css` | new — ghost icon-button style |
| `website/src/theme/Navbar/ColorModeToggle/index.js` | new — swizzle injecting the navbar button |
| `website/src/components/HomepageHero/index.js` | mount `<OnboardAgentButton />`; hero-contrast style |
| `website/static/start.md` | new — ZIO HTTP agent onboarding guide |
| `website/src/components/HomepageCodingAgent/` | delete directory |
| `website/src/pages/index.js` | remove `HomepageCodingAgent` import + mount |

## Verification

- `npm run build` (from `website/`) succeeds.
- SSR check on `build/index.html`: contains `Onboard your agent to ZIO HTTP`.
- `build/start.md` exists and contains `zio-http-knowledge`.
- Browser check at `localhost:3000`, both themes:
  - Hero pill copies the prompt; label flips to `Copied!` for ~2s; pill is legible
    on the dark hero banner.
  - Navbar terminal icon copies the same prompt (flips to a check).
  - Hover accent is green in both themes.
  - No Infima/Tailwind reset regressions on docs pages (spot-check a docs page and
    the footer).

## Out of scope

- The rest of the homepage modernization (CodeShowcase, Reveal, SectionWrapper,
  RepoStats, ambient background, fullpage scroll, glass surfaces). Tailwind is added
  here but only consumed by the onboard button; other sections stay as-is.
- Announcement bar restyle.
- Any docs content changes.

## Notes / open risks

- **Docusaurus 3.10 + Tailwind v4:** the `@import 'tailwindcss/...'` + `@config`
  approach requires `@tailwindcss/postcss` v4; zio.dev runs the same on Docusaurus
  3.x. Pin the same `4.3.3` to reduce surprises.
- **`important: true`** makes every Tailwind utility `!important`. This is
  intentional (utilities must beat Infima) and matches zio.dev, but means any future
  component overriding a utility must also use `!important` or a later layer.
- **Skill/plugin names in `start.md`** (`zio/zio-skills`, `zio-skills@ziogenetics`,
  `/reload-plugins`) are copied from zio.dev's live `start.md`; confirm they are the
  correct published identifiers before shipping.
