# Website Design Principles

This document captures the design principles and conventions followed in the ZIO HTTP website. Use these guidelines for consistency in future development.

---

## Component Architecture

### File Structure
Each component follows a consistent directory structure:
```
ComponentName/
  ├── index.js                 # React component
  └── styles.module.css        # CSS Modules styles
```

**Why CSS Modules?**
- Scoped styles prevent naming conflicts
- Works well with Docusaurus/Infima
- No build complexity (no Tailwind, styled-components, etc.)
- Aligns with existing project conventions

---

## Color & Theme System

### Using Infima Variables
Always use Infima CSS variables for theme support (automatic light/dark mode):

```css
/* ✅ Good - respects light/dark mode */
color: var(--ifm-heading-color);
background-color: var(--ifm-background-surface-color);

/* ❌ Avoid - hardcoded colors */
color: #333;
background-color: #fff;
```

**Key Infima Variables:**
- `--ifm-background-surface-color` - section backgrounds (white/light in light mode)
- `--ifm-heading-color` - heading text color
- `--ifm-text-color-secondary` - secondary text
- `--ifm-color-emphasis-100` - light gray background
- `--ifm-color-primary` - brand green color
- `--ifm-card-background-color` - card backgrounds

### Alternating Section Backgrounds

Homepage sections follow a **white → gray → white → gray** pattern for visual rhythm:

```
1. HomepageCodeSnippet     → white (var(--ifm-background-surface-color))
2. HomepageFeatures        → gray (var(--ifm-color-emphasis-100))
3. HomepageEcosystem       → white (var(--ifm-background-surface-color))
4. HomepageZionomicon      → gray (var(--ifm-color-emphasis-100))
5. HomepageUsers           → white (var(--ifm-background-surface-color))
```

**Why `emphasis-100` for gray?**
- More visible than `emphasis-0` in light mode
- Maintains contrast in dark mode
- Creates clear visual separation

---

## Interactive Components

### Syntax Highlighting

Use `prism-react-renderer` v1.x with dracula theme for code panels:

```jsx
import Highlight, { defaultProps } from 'prism-react-renderer';
import dracula from 'prism-react-renderer/themes/dracula';

<Highlight
  {...defaultProps}
  theme={dracula}
  code={code.trim()}
  language="scala"
>
  {({ className, style, tokens, getLineProps, getTokenProps }) => (
    <pre className={className} style={style}>
      <code>
        {tokens.map((line, i) => (
          <div key={i} {...getLineProps({ line, key: i })}>
            <span className={styles.lineNumber}>{i + 1}</span>
            {line.map((token, key) => (
              <span key={key} {...getTokenProps({ token, key })} />
            ))}
          </div>
        ))}
      </code>
    </pre>
  )}
</Highlight>
```

**Key points:**
- Always call `.trim()` on code strings to remove artificial leading/trailing newlines
- Use `key={activeTab}` to force remount on state change
- Line numbers use `user-select: none` so copy gets only code

### Icons

Use `react-icons/fa6` for consistency:

```jsx
import { FaCopy, FaCheck, FaArrowRight } from 'react-icons/fa6';

<FaCopy size={14} aria-hidden="true" />
```

**Why fa6?**
- Already a project dependency
- Large icon library
- Tree-shakeable

---

## Responsive Design

### Breakpoints

Use Docusaurus's standard breakpoint of **996px**:

```css
@media screen and (max-width: 996px) {
  /* Mobile/tablet adjustments */
  .gridLayout {
    grid-template-columns: 1fr; /* Stack vertically */
  }
}
```

### Layout Patterns

**Two-column desktop → single-column mobile:**
```css
.container {
  display: grid;
  grid-template-columns: 2fr 3fr;  /* desktop: 40% / 60% */
  gap: 4rem;
}

@media (max-width: 996px) {
  .container {
    grid-template-columns: 1fr;   /* mobile: stack */
    gap: 2rem;
  }
}
```

---

## Accessibility

### Semantic HTML & ARIA

For interactive components, use semantic attributes:

```jsx
{/* Tab buttons */}
<button
  role="tab"
  aria-selected={activeTab === idx}
  onClick={() => setActiveTab(idx)}
>
  Tab Label
</button>

{/* Copy button with context-aware messaging */}
<button
  aria-label={copied ? 'Copied!' : 'Copy code'}
  title={copied ? 'Copied!' : 'Copy to clipboard'}
>
  {copied ? <FaCheck /> : <FaCopy />}
</button>
```

### Non-selectable Elements

For UI elements that shouldn't be copied:

```css
.lineNumber {
  user-select: none;
  font-variant-numeric: tabular-nums; /* fixed-width numbers */
}
```

---

## Server-Side Rendering (SSR) Safety

### Browser-Only APIs

Guard browser-only APIs like `navigator.clipboard`:

```jsx
import useIsBrowser from '@docusaurus/useIsBrowser';

export function MyComponent() {
  const isBrowser = useIsBrowser();

  const handleCopy = () => {
    if (!isBrowser) return;
    navigator.clipboard.writeText(text).then(() => {
      // success
    });
  };

  return (
    <>
      {isBrowser && <button onClick={handleCopy}>Copy</button>}
    </>
  );
}
```

**Why?**
- `navigator.clipboard` throws on server
- `useIsBrowser()` prevents hydration mismatches
- Gracefully degrades if JS disabled

### Scala Syntax Highlighting on SSR

`prism-react-renderer` doesn't include Scala in its bundled Prism. Docusaurus loads it at browser startup via `prism-include-languages.js`. Result:
- **SSR**: plain text (no highlighting)
- **Client**: full highlighting after hydration
- **Impact**: None — user sees no visual change, Prism automatically reconciles

---

## State Management

### React Hooks

Use simple React hooks for component state:

```jsx
const [activeTab, setActiveTab] = useState(0);
const [copied, setCopied] = useState(false);

const handleTabClick = (idx) => {
  setActiveTab(idx);
  setCopied(false);  // reset feedback state on tab change
};
```

**Why?**
- Lightweight, no Redux/Context needed
- Clear intent and data flow
- Easy to understand and maintain

---

## Styling Best Practices

### Variable Naming

Use clear, descriptive names:

```css
/* ✅ Good */
.codeSnippetSection { }
.tabBar { }
.copyButton { }
.lineNumber { }

/* ❌ Avoid */
.s1 { }
.tb { }
.cb { }
.ln { }
```

### Organizing CSS

Group related styles logically:

```css
/* Section wrapper */
.section { }
.innerContainer { }

/* Left column */
.leftColumn { }
.leftColumn h2 { }
.leftColumn p { }

/* Right column with code panel */
.rightColumn { }
.codePanel { }
.tabBar { }
.tab { }
.tabActive { }
.codeArea { }
.pre { }
.toolbar { }
```

### Hover & Active States

Use subtle transitions:

```css
.button {
  transition: all 0.15s ease;
  border: 1px solid rgba(255, 255, 255, 0.15);
  color: #8b949e;
}

.button:hover {
  border-color: var(--ifm-color-primary);
  color: var(--ifm-color-primary);
}
```

---

## Code Example Conventions

### Tab Data Structure

Define reusable tab data at module level:

```jsx
const TABS = [
  {
    label: 'Create an HTTP Server',
    code: `import zio._
import zio.http._
...`,
  },
  {
    label: 'Define Endpoints',
    code: `...`,
  },
];
```

**Benefits:**
- Stable reference (doesn't recreate on every render)
- Easy to extend or modify
- Clear data organization

### Code Strings

Always trim template literals:

```jsx
// ✅ Good
code={TABS[activeTab].code.trim()}

// ❌ Avoid (leaves blank first line)
code={TABS[activeTab].code}
```

---

## Testing & Verification Checklist

When building homepage sections:

- [ ] Renders correctly in both light and dark modes
- [ ] Responsive: desktop layout at ≥996px, mobile stacking at ≤996px
- [ ] Interactive elements have `role`, `aria-*`, and `title` attributes
- [ ] Non-selectable UI elements use `user-select: none`
- [ ] Browser-only APIs guarded with `useIsBrowser()`
- [ ] Uses Infima variables for colors (not hardcoded)
- [ ] Follows alternating section background pattern
- [ ] Icons from `react-icons/fa6`
- [ ] CSS organized and well-commented
- [ ] No console warnings in dev mode

---

## Common Patterns

### Conditional Rendering with Theme Variables

```jsx
<div style={{ color: 'var(--ifm-heading-color)' }}>
  Automatically light or dark
</div>
```

### Horizontal Scrollable Container

```css
.scrollContainer {
  display: flex;
  overflow-x: auto;
  scrollbar-width: none; /* Firefox */
}

.scrollContainer::-webkit-scrollbar {
  display: none; /* Chrome/Safari */
}
```

### Center Content in Grid

```css
.container {
  display: grid;
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 2rem;
}
```

---

## Resources

- **Docusaurus Styling**: https://docusaurus.io/docs/styling-layout
- **Infima Variables**: Built-in CSS framework, see `:root` in browser DevTools
- **Prism Themes**: `prism-react-renderer/themes/`
- **React Icons**: https://react-icons.github.io/react-icons/

---

**Last Updated:** April 2026  
**Project:** ZIO HTTP Website (Docusaurus 2.2.0)
