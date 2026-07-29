# Agent Onboard for ziohttp.com Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add hero + navbar "Onboard your agent" copy-prompt buttons and a static `start.md` guide to the ZIO HTTP website, replacing the old install-command section, on a new Tailwind v4 foundation.

**Architecture:** Wire Tailwind v4 into the Docusaurus website via a PostCSS plugin, then build a Tailwind-styled `OnboardAgentButton` (hero pill + compact navbar variant sharing one `PROMPT`/`copyPrompt` module). A `Navbar/ColorModeToggle` swizzle injects the navbar button. A static `website/static/start.md` is what the copied prompt tells the agent to fetch. The old `HomepageCodingAgent` section is removed.

**Tech Stack:** Docusaurus 3.10, React 18 (JSX), Tailwind v4 (`@tailwindcss/postcss` 4.3.3), `react-icons/fa6` (existing dep).

## Global Constraints

- **No unit-test harness on this website.** The verification cycle for every task is `npm run build` (from `website/`) + SSR-HTML grep + manual browser check at `localhost:3000`. There are no Jest/Vitest tests to write.
- Domain: `ziohttp.com`. All absolute links in copy point there.
- Brand primary is **green** (`--ifm-color-primary: #81c784`); the Tailwind `primary` token maps to `colors.green` (DEFAULT `green[600]`). Never red.
- Tailwind dependency pinned to `@tailwindcss/postcss@4.3.3` (same as zio.dev).
- `important: true` in `tailwind.config.js` — every utility is `!important` (intentional, so utilities beat Infima).
- All website source lives under `website/`. Run all `npm`/build commands from `website/`.
- Specs and plans live in the repo-root `specs/` directory, never in `docs/` (the `docs/` tree is mdoc-published to `website/docs`).
- Commits: Conventional Commits; end each commit message with `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.
- Work on the current branch `modern-website`.
- Exact copy string (used verbatim in Task 2 and reused in Task 3):
  `Fetch https://ziohttp.com/start.md and follow the instructions to set up my environment for ZIO HTTP development.`

---

## File Structure

New files:
- `website/postcss.config.js` — PostCSS Tailwind plugin.
- `website/tailwind.config.js` — content globs, dark-mode hook, green `primary`.
- `website/src/components/OnboardAgentButton/index.jsx` — hero button + `PROMPT`/`copyPrompt`.
- `website/src/components/OnboardAgentButton/logos.jsx` — four agent SVG logos.
- `website/src/components/OnboardAgentButton/NavbarButton.jsx` — compact navbar variant.
- `website/src/components/OnboardAgentButton/NavbarButton.module.css` — navbar button style.
- `website/src/theme/Navbar/ColorModeToggle/index.js` — swizzle injecting the navbar button.
- `website/static/start.md` — agent onboarding guide.

Modified:
- `website/package.json` — add Tailwind dependency.
- `website/docusaurus.config.js` — add Tailwind PostCSS plugin.
- `website/src/css/custom.css` — prepend Tailwind imports + `@config` + border-compat base.
- `website/src/components/HomepageHero/index.js` — mount the hero button.
- `website/src/components/HomepageHero/styles.module.css` — gap for the button row.
- `website/src/pages/index.js` — remove `HomepageCodingAgent` import + mount.

Deleted:
- `website/src/components/HomepageCodingAgent/` (`index.js`, `styles.module.css`).

---

### Task 1: Tailwind v4 foundation

**Files:**
- Modify: `website/package.json` (dependencies)
- Modify: `website/docusaurus.config.js` (`plugins` array)
- Create: `website/postcss.config.js`
- Create: `website/tailwind.config.js`
- Modify: `website/src/css/custom.css` (prepend)

**Interfaces:**
- Produces: an active Tailwind v4 utility layer consumed by Tasks 2 and 3 (utility classes like `flex`, `rounded-full`, `hover:text-primary` resolve, with `primary` = green).

- [ ] **Step 1: Add the Tailwind dependency**

In `website/package.json`, add to `"dependencies"` (after the `"@mdx-js/react": "3",` line):

```json
    "@tailwindcss/postcss": "4.3.3",
```

- [ ] **Step 2: Create the PostCSS config**

Create `website/postcss.config.js`:

```js
module.exports = {
  plugins: [require('@tailwindcss/postcss')],
};
```

- [ ] **Step 3: Create the Tailwind config**

Create `website/tailwind.config.js`:

```js
const colors = require('tailwindcss/colors');

/** @type {import('tailwindcss').Config} */
module.exports = {
  important: true,
  content: ['./src/**/*.{js,jsx,ts,tsx}', './docs/**/*.mdx'],
  darkMode: ['class', '[data-theme="dark"]'], // hooks into docusaurus' dark mode
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

- [ ] **Step 4: Register the Tailwind PostCSS plugin in Docusaurus**

In `website/docusaurus.config.js`, find the `plugins: [` array (it starts with the `'docusaurus-plugin-llms'` entry) and insert this function as the **first** element of the array, immediately after `plugins: [`:

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

- [ ] **Step 5: Prepend the Tailwind imports to custom.css**

At the very top of `website/src/css/custom.css` (above the existing `/** Any CSS included here... */` comment), insert:

```css
@layer theme, base, components, utilities;
@import 'tailwindcss/theme.css' layer(theme);
@import 'tailwindcss/utilities.css' layer(utilities);

@config '../../tailwind.config.js';

/* Tailwind v4 changed the default border color to currentColor; restore the
   v3 gray default so existing Infima-bordered elements are unchanged. */
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

- [ ] **Step 6: Install and build**

Run from `website/`:

```bash
npm install
npm run build
```

Expected: `npm install` adds `@tailwindcss/postcss`; `npm run build` succeeds with no PostCSS/CSS errors.

- [ ] **Step 7: Verify no visual regression**

Run `npm run start` (or serve `build/`), open `localhost:3000`. Confirm the homepage, a docs page, navbar, and footer look unchanged from before (Tailwind's reset + `border-compat` block should leave Infima styling intact). No utility classes are used yet, so nothing new should appear.

- [ ] **Step 8: Commit**

```bash
git add website/package.json website/package-lock.json website/postcss.config.js website/tailwind.config.js website/docusaurus.config.js website/src/css/custom.css
git commit -m "build(website): add Tailwind v4 foundation

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: OnboardAgentButton (hero button + logos)

**Files:**
- Create: `website/src/components/OnboardAgentButton/index.jsx`
- Create: `website/src/components/OnboardAgentButton/logos.jsx`
- Modify: `website/src/components/HomepageHero/index.js`
- Modify: `website/src/components/HomepageHero/styles.module.css`

**Interfaces:**
- Consumes: Tailwind utility layer from Task 1.
- Produces: `PROMPT` (string), `copyPrompt(text): Promise<boolean>`, and default export `OnboardAgentButton({ tone })` where `tone` is `'default' | 'onDark'` (default `'default'`). Named logo exports `ClaudeLogo`, `CodexLogo`, `CursorLogo`, `OpenCodeLogo` from `./logos`. Task 3 imports `PROMPT` and `copyPrompt` from `./index`.

- [ ] **Step 1: Create the logos module**

Create `website/src/components/OnboardAgentButton/logos.jsx`:

```jsx
import React from 'react';

// Anthropic Claude — official mark (keeps its brand color in both themes)
export function ClaudeLogo(props) {
  return (
    <svg viewBox="0 0 256 257" className="h-5 w-5 shrink-0" aria-hidden="true" {...props}>
      <path
        fill="#D97757"
        d="m50.228 170.321 50.357-28.257.843-2.463-.843-1.361h-2.462l-8.426-.518-28.775-.778-24.952-1.037-24.175-1.296-6.092-1.297L0 125.796l.583-3.759 5.12-3.434 7.324.648 16.202 1.101 24.304 1.685 17.629 1.037 26.118 2.722h4.148l.583-1.685-1.426-1.037-1.101-1.037-25.147-17.045-27.22-18.017-14.258-10.37-7.713-5.25-3.888-4.925-1.685-10.758 7-7.713 9.397.649 2.398.648 9.527 7.323 20.35 15.75L94.817 91.9l3.889 3.24 1.555-1.102.195-.777-1.75-2.917-14.453-26.118-15.425-26.572-6.87-11.018-1.814-6.61c-.648-2.723-1.102-4.991-1.102-7.778l7.972-10.823L71.42 0 82.05 1.426l4.472 3.888 6.61 15.101 10.694 23.786 16.591 32.34 4.861 9.592 2.592 8.879.973 2.722h1.685v-1.556l1.36-18.211 2.528-22.36 2.463-28.776.843-8.1 4.018-9.722 7.971-5.25 6.222 2.981 5.12 7.324-.713 4.73-3.046 19.768-5.962 30.98-3.889 20.739h2.268l2.593-2.593 10.499-13.934 17.628-22.036 7.778-8.749 9.073-9.657 5.833-4.601h11.018l8.1 12.055-3.628 12.443-11.342 14.388-9.398 12.184-13.48 18.147-8.426 14.518.778 1.166 2.01-.194 30.46-6.481 16.462-2.982 19.637-3.37 8.88 4.148.971 4.213-3.5 8.62-20.998 5.184-24.628 4.926-36.682 8.685-.454.324.519.648 16.526 1.555 7.065.389h17.304l32.21 2.398 8.426 5.574 5.055 6.805-.843 5.184-12.962 6.611-17.498-4.148-40.83-9.721-14-3.5h-1.944v1.167l11.666 11.406 21.387 19.314 26.767 24.887 1.36 6.157-3.434 4.86-3.63-.518-23.526-17.693-9.073-7.972-20.545-17.304h-1.36v1.814l4.73 6.935 25.017 37.59 1.296 11.536-1.814 3.76-6.481 2.268-7.13-1.297-14.647-20.544-15.1-23.138-12.185-20.739-1.49.843-7.194 77.448-3.37 3.953-7.778 2.981-6.48-4.925-3.436-7.972 3.435-15.749 4.148-20.544 3.37-16.333 3.046-20.285 1.815-6.74-.13-.454-1.49.194-15.295 20.999-23.267 31.433-18.406 19.702-4.407 1.75-7.648-3.954.713-7.064 4.277-6.286 25.47-32.405 15.36-20.092 9.917-11.6-.065-1.686h-.583L44.07 198.125l-12.055 1.555-5.185-4.86.648-7.972 2.463-2.593 20.35-13.999-.064.065Z"
      />
    </svg>
  );
}

// OpenAI / Codex
export function CodexLogo(props) {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-5 w-5 shrink-0"
      fill="currentColor"
      fillRule="evenodd"
      aria-hidden="true"
      {...props}
    >
      <path
        clipRule="evenodd"
        d="M8.086.457a6.105 6.105 0 013.046-.415c1.333.153 2.521.72 3.564 1.7a.117.117 0 00.107.029c1.408-.346 2.762-.224 4.061.366l.063.03.154.076c1.357.703 2.33 1.77 2.918 3.198.278.679.418 1.388.421 2.126a5.655 5.655 0 01-.18 1.631.167.167 0 00.04.155 5.982 5.982 0 011.578 2.891c.385 1.901-.01 3.615-1.183 5.14l-.182.22a6.063 6.063 0 01-2.934 1.851.162.162 0 00-.108.102c-.255.736-.511 1.364-.987 1.992-1.199 1.582-2.962 2.462-4.948 2.451-1.583-.008-2.986-.587-4.21-1.736a.145.145 0 00-.14-.032c-.518.167-1.04.191-1.604.185a5.924 5.924 0 01-2.595-.622 6.058 6.058 0 01-2.146-1.781c-.203-.269-.404-.522-.551-.821a7.74 7.74 0 01-.495-1.283 6.11 6.11 0 01-.017-3.064.166.166 0 00.008-.074.115.115 0 00-.037-.064 5.958 5.958 0 01-1.38-2.202 5.196 5.196 0 01-.333-1.589 6.915 6.915 0 01.188-2.132c.45-1.484 1.309-2.648 2.577-3.493.282-.188.55-.334.802-.438.286-.12.573-.22.861-.304a.129.129 0 00.087-.087A6.016 6.016 0 015.635 2.31C6.315 1.464 7.132.846 8.086.457zm-.804 7.85a.848.848 0 00-1.473.842l1.694 2.965-1.688 2.848a.849.849 0 001.46.864l1.94-3.272a.849.849 0 00.007-.854l-1.94-3.393zm5.446 6.24a.849.849 0 000 1.695h4.848a.849.849 0 000-1.696h-4.848z"
      />
    </svg>
  );
}

// Cursor
export function CursorLogo(props) {
  return (
    <svg
      viewBox="0 0 466.73 532.09"
      className="h-5 w-5 shrink-0"
      fill="currentColor"
      aria-hidden="true"
      {...props}
    >
      <path d="M457.43,125.94L244.42,2.96c-6.84-3.95-15.28-3.95-22.12,0L9.3,125.94c-5.75,3.32-9.3,9.46-9.3,16.11v247.99c0,6.65,3.55,12.79,9.3,16.11l213.01,122.98c6.84,3.95,15.28,3.95,22.12,0l213.01-122.98c5.75-3.32,9.3-9.46,9.3-16.11v-247.99c0-6.65-3.55-12.79-9.3-16.11h-.01ZM444.05,151.99l-205.63,356.16c-1.39,2.4-5.06,1.42-5.06-1.36v-233.21c0-4.66-2.49-8.97-6.53-11.31L24.87,145.67c-2.4-1.39-1.42-5.06,1.36-5.06h411.26c5.84,0,9.49,6.33,6.57,11.39h-.01Z" />
    </svg>
  );
}

// OpenCode
export function OpenCodeLogo(props) {
  return (
    <svg
      width="32"
      height="40"
      viewBox="0 0 32 40"
      fill="none"
      className="h-4.5 w-auto shrink-0"
      aria-hidden="true"
      {...props}
    >
      <g clipPath="url(#opencode-clip)">
        <path d="M24 32H8V16H24V32Z" fill="currentColor" fillOpacity="0.3" />
        <path d="M24 8H8V32H24V8ZM32 40H0V0H32V40Z" fill="currentColor" />
      </g>
      <defs>
        <clipPath id="opencode-clip">
          <rect width="32" height="40" fill="white" />
        </clipPath>
      </defs>
    </svg>
  );
}
```

- [ ] **Step 2: Create the button module**

Create `website/src/components/OnboardAgentButton/index.jsx`. The `tone` prop switches border/text colors so the pill is legible on the light homepage body (`default`) or the dark hero banner (`onDark`):

```jsx
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { ClaudeLogo, CodexLogo, CursorLogo, OpenCodeLogo } from './logos';

export const PROMPT =
  'Fetch https://ziohttp.com/start.md and follow the instructions to set up my environment for ZIO HTTP development.';

export async function copyPrompt(text) {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch (_) {
    // fall through to legacy path
  }
  try {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.opacity = '0';
    document.body.appendChild(ta);
    ta.focus();
    ta.select();
    const ok = document.execCommand('copy');
    document.body.removeChild(ta);
    return ok;
  } catch (_) {
    return false;
  }
}

export default function OnboardAgentButton({ tone = 'default' }) {
  const [copied, setCopied] = useState(false);
  const timer = useRef(null);

  useEffect(() => () => timer.current && clearTimeout(timer.current), []);

  const onClick = useCallback(async () => {
    const ok = await copyPrompt(PROMPT);
    if (!ok) {
      window.prompt('Copy this prompt for your coding agent:', PROMPT);
      return;
    }
    setCopied(true);
    timer.current && clearTimeout(timer.current);
    timer.current = setTimeout(() => setCopied(false), 2000);
  }, []);

  const toneClasses =
    tone === 'onDark'
      ? 'border-white/40 text-white hover:border-primary hover:text-primary'
      : 'border-zinc-300 text-zinc-800 hover:border-primary hover:text-primary dark:border-zinc-700 dark:text-zinc-100';

  return (
    <button
      type="button"
      onClick={onClick}
      title="Copy the prompt to onboard your coding agent to ZIO HTTP"
      aria-label="Copy the ZIO HTTP agent onboarding prompt to the clipboard"
      className={`flex items-center gap-2 rounded-full border px-6 py-2.5 text-base font-semibold leading-normal transition-colors ${toneClasses}`}
    >
      <span>{copied ? 'Copied!' : 'Onboard your agent to ZIO HTTP'}</span>
      <span className="flex items-center gap-1" aria-hidden="true">
        <ClaudeLogo />
        <CodexLogo />
        <CursorLogo />
        <OpenCodeLogo />
      </span>
      <span className="sr-only">
        Works with Claude, Codex, Cursor, and OpenCode
      </span>
    </button>
  );
}
```

- [ ] **Step 3: Mount the button in the Hero**

In `website/src/components/HomepageHero/index.js`, add the import after the existing `styles` import:

```js
import OnboardAgentButton from '@site/src/components/OnboardAgentButton';
```

Then, inside the `.buttons` div, add the button after the existing "Get Started" `<Link>...</Link>` (still inside the same `<div className={styles.buttons}>`):

```jsx
            <OnboardAgentButton tone="onDark" />
```

- [ ] **Step 4: Add spacing to the button row**

In `website/src/components/HomepageHero/styles.module.css`, change the `.buttons` rule (currently `display: flex; align-items: center;`) to:

```css
.buttons {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 1rem;
}
```

- [ ] **Step 5: Build and verify SSR output**

Run from `website/`: `npm run build`
Expected: build succeeds. Then:

```bash
grep -c "Onboard your agent to ZIO HTTP" build/index.html   # expected: >=1
```

- [ ] **Step 6: Browser check**

At `localhost:3000` homepage:
- Pill appears in the hero next to "Get Started", legible on the dark banner (white border/text).
- Click copies the prompt; label flips to "Copied!" for ~2s, then back.
- Paste into a scratch buffer to confirm the exact `PROMPT` string.
- Hover turns border/text green.
- Toggle dark/light: hero pill unaffected (it is `onDark` in both, over the dark banner).

- [ ] **Step 7: Commit**

```bash
git add website/src/components/OnboardAgentButton/index.jsx website/src/components/OnboardAgentButton/logos.jsx website/src/components/HomepageHero/index.js website/src/components/HomepageHero/styles.module.css
git commit -m "feat(website): add Onboard-your-agent hero button

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: Navbar button + ColorModeToggle swizzle

**Files:**
- Create: `website/src/components/OnboardAgentButton/NavbarButton.jsx`
- Create: `website/src/components/OnboardAgentButton/NavbarButton.module.css`
- Create: `website/src/theme/Navbar/ColorModeToggle/index.js`

**Interfaces:**
- Consumes: `PROMPT`, `copyPrompt` from `./index` (Task 2); Tailwind is not used here (CSS module + Infima vars).

- [ ] **Step 1: Create the navbar button style**

Create `website/src/components/OnboardAgentButton/NavbarButton.module.css`:

```css
/* Ghost icon button that blends into the navbar like the other items
   (GitHub, FAQ) — navbar link color, brand green on hover. */
.button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin: 0 0.4rem;
  padding: 0.35rem;
  border: none;
  background: transparent;
  color: var(--ifm-navbar-link-color);
  font-size: 1.25rem;
  line-height: 0;
  cursor: pointer;
  transition: color 0.2s ease;
}

.button:hover {
  color: var(--ifm-color-primary);
}
```

- [ ] **Step 2: Create the navbar button component**

Create `website/src/components/OnboardAgentButton/NavbarButton.jsx`:

```jsx
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { FaCheck, FaTerminal } from 'react-icons/fa6';

import { PROMPT, copyPrompt } from './index';
import styles from './NavbarButton.module.css';

// Compact navbar variant of the hero's "Onboard your agent to ZIO HTTP" button:
// copies the same setup prompt to the clipboard, shown as a small icon pill.
export default function OnboardAgentNavbarButton() {
  const [copied, setCopied] = useState(false);
  const timer = useRef(null);

  useEffect(() => () => timer.current && clearTimeout(timer.current), []);

  const onClick = useCallback(async () => {
    const ok = await copyPrompt(PROMPT);
    if (!ok) {
      window.prompt('Copy this prompt for your coding agent:', PROMPT);
      return;
    }
    setCopied(true);
    timer.current && clearTimeout(timer.current);
    timer.current = setTimeout(() => setCopied(false), 2000);
  }, []);

  return (
    <button
      type="button"
      onClick={onClick}
      title="Copy the prompt to onboard your coding agent to ZIO HTTP"
      aria-label="Copy the ZIO HTTP agent onboarding prompt to the clipboard"
      className={styles.button}
    >
      {copied ? (
        <FaCheck aria-hidden="true" />
      ) : (
        <FaTerminal aria-hidden="true" />
      )}
    </button>
  );
}
```

- [ ] **Step 3: Create the ColorModeToggle swizzle**

Create `website/src/theme/Navbar/ColorModeToggle/index.js`:

```jsx
import React from 'react';
import ColorModeToggle from '@theme-original/Navbar/ColorModeToggle';
import OnboardAgentNavbarButton from '@site/src/components/OnboardAgentButton/NavbarButton';

// Render the compact Onboard Agent button just before the light/dark theme
// switch — i.e. after the GitHub link (last right item) and before the toggle.
export default function ColorModeToggleWrapper(props) {
  return (
    <>
      <OnboardAgentNavbarButton />
      <ColorModeToggle {...props} />
    </>
  );
}
```

- [ ] **Step 4: Build and verify SSR output**

Run from `website/`: `npm run build`
Expected: build succeeds (the swizzle wraps the original component; Docusaurus may print a swizzle "unsafe/safe" note — that is fine). Then:

```bash
grep -c "onboarding prompt to the clipboard" build/index.html   # expected: >=1
```

- [ ] **Step 5: Browser check**

At `localhost:3000`:
- A terminal icon appears in the navbar just left of the theme toggle.
- Click copies the same `PROMPT`; icon flips to a check for ~2s.
- Hover turns the icon green.
- Icon color follows navbar link color in both light and dark themes.

- [ ] **Step 6: Commit**

```bash
git add website/src/components/OnboardAgentButton/NavbarButton.jsx website/src/components/OnboardAgentButton/NavbarButton.module.css website/src/theme/Navbar/ColorModeToggle/index.js
git commit -m "feat(website): add Onboard-your-agent navbar button

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: start.md guide

**Files:**
- Create: `website/static/start.md`

**Interfaces:**
- Consumes: nothing (static asset). It is the target of the `PROMPT` URL from Task 2.

- [ ] **Step 1: Create the guide**

Create `website/static/start.md`:

```markdown
# Get your agent ready for ZIO HTTP

Official guide for setting up an AI coding agent to build with [ZIO HTTP](https://ziohttp.com), a next-generation Scala framework for building scalable, correct, and efficient HTTP clients and servers.

Install the ZIO skills below so your agent answers from live, accurate ZIO HTTP documentation instead of stale training data.

## Claude Code

Install the plugin — it bundles both the ZIO Knowledge and ZIO HTTP Knowledge skills:

​```
claude plugin marketplace add zio/zio-skills
claude plugin install zio-skills@ziogenetics
​```

Then reload plugins with the `/reload-plugins` command to activate the skills.

## Other agents (Codex, OpenCode, Cursor, etc.)

Install the skills with the `skills` CLI.

ZIO HTTP Knowledge (required):

​```
npx skills add zio/zio-skills --skill zio-http-knowledge
​```

ZIO Knowledge (recommended — ZIO HTTP builds on core ZIO):

​```
npx skills add zio/zio-skills --skill zio-knowledge
​```

## Fallback

If you cannot install the skills, fetch the documentation index yourself and use it to find the right pages:

- https://ziohttp.com/llms.txt — ZIO HTTP
- https://zio.dev/llms.txt — ZIO
```

> **Implementer note:** the three code blocks above are shown with a zero-width marker (`​`) before each ``` ``` `` fence only to keep this plan's own Markdown from breaking. In the real `website/static/start.md`, use plain triple-backtick fences with no marker.

- [ ] **Step 2: Build and verify the asset is served**

Run from `website/`: `npm run build`
Expected: build succeeds. Then:

```bash
test -f build/start.md && echo "start.md served"     # expected: start.md served
grep -c "zio-http-knowledge" build/start.md          # expected: >=1
grep -c "ziohttp.com/llms.txt" build/start.md        # expected: >=1
```

- [ ] **Step 3: Browser check**

Open `localhost:3000/start.md` — the raw guide loads. Confirm the Claude Code plugin commands, the two `npx skills add` commands (http required, core recommended), and both `llms.txt` fallback links are present.

- [ ] **Step 4: Verify the plugin/skill identifiers**

Confirm `zio/zio-skills`, `zio-skills@ziogenetics`, `/reload-plugins`, `zio-http-knowledge`, and `zio-knowledge` are the correct published identifiers (cross-check against zio.dev's live `start.md` at https://zio.dev/start.md). Fix any mismatch before committing.

- [ ] **Step 5: Commit**

```bash
git add website/static/start.md
git commit -m "docs(website): add start.md agent onboarding guide

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: Remove the old HomepageCodingAgent section

**Files:**
- Delete: `website/src/components/HomepageCodingAgent/index.js`
- Delete: `website/src/components/HomepageCodingAgent/styles.module.css`
- Modify: `website/src/pages/index.js`

**Interfaces:**
- Consumes: nothing. Removes the superseded install-command section now that the button + `start.md` are the onboarding path.

- [ ] **Step 1: Remove the import and mount**

In `website/src/pages/index.js`:
- Delete the line `import HomepageCodingAgent from '@site/src/components/HomepageCodingAgent';`
- Delete the line `<HomepageCodingAgent />` from inside `<main>`.

Resulting `<main>` block:

```jsx
      <main>
        <HomepageCodeSnippet />
        <HomepageFeatures />
        <HomepageEcosystem />
        <HomepageZionomicon />
        <HomepageUsers />
      </main>
```

- [ ] **Step 2: Delete the component directory**

```bash
git rm -r website/src/components/HomepageCodingAgent
```

- [ ] **Step 3: Build and verify removal**

Run from `website/`: `npm run build`
Expected: build succeeds with no unresolved-import error. Then:

```bash
grep -c "Teach Your Coding Agent" build/index.html   # expected: 0
```

- [ ] **Step 4: Browser check**

At `localhost:3000` homepage: the old "Teach Your Coding Agent…" section is gone; sections flow Hero → CodeSnippet → Features → Ecosystem → Zionomicon → Users. The onboarding path is now the hero pill + navbar button.

- [ ] **Step 5: Commit**

```bash
git add website/src/pages/index.js
git commit -m "refactor(website): remove HomepageCodingAgent in favor of onboard button

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage:**
- Spec A (Tailwind foundation) → Task 1. ✓
- Spec B (OnboardAgentButton index/logos/navbar) → Task 2 (index+logos) + Task 3 (navbar). ✓
- Spec C (ColorModeToggle swizzle, Hero mount, contrast) → Task 3 (swizzle) + Task 2 (Hero mount + `tone="onDark"` contrast fix + `.buttons` gap). ✓
- Spec D (start.md) → Task 4. ✓
- Spec E (remove HomepageCodingAgent) → Task 5. ✓
- Spec F (verification) → each task's build + SSR grep + browser steps. ✓

**Type consistency:** `PROMPT` / `copyPrompt` produced in Task 2 `index.jsx`, consumed in Task 3 `NavbarButton.jsx` — names and import path (`./index`) match. `OnboardAgentButton({ tone })` default export mounted in Hero with `tone="onDark"`. Logo export names (`ClaudeLogo`/`CodexLogo`/`CursorLogo`/`OpenCodeLogo`) match between `logos.jsx` and `index.jsx`. ✓

**Placeholder scan:** no TBD/TODO; all code blocks are complete; the one non-literal (zero-width fence marker in Task 4) is explicitly called out with the exact fix. ✓

**Scope:** single focused feature, five independently-reviewable tasks. ✓
