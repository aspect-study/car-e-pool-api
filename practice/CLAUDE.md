# practice/ — Domain Q&A Practice Pages

This folder contains static HTML practice pages for CCA-F exam study.
Each file maps to one exam domain. All pages are served via GitHub Pages (no backend).

## File Naming

| File | Domain |
|---|---|
| `d1.html` | D1: Agentic Systems (27%) |
| `d2.html` | D2: Tools & MCP (18%) |
| `d3.html` | D3: Claude Code (20%) |
| `d4.html` | D4: Prompt Engineering (20%) |
| `d5.html` | D5: Context & Memory (15%) |

## Architecture

- **Pure static HTML** — no API, no database, no build step
- **localStorage only** — score preference and last session score persist per browser
- **Self-contained** — each `d#.html` includes all CSS and JS inline (no external deps except the theme token)
- **Back navigation** — every page has a `← Back to Study Guide` button linking to `../index.html`

## Score / Progress Feature

Score tracking is opt-in and runs entirely in the browser:

```js
// Keys used in localStorage
'practice-score-visible'   // 'true' | 'false'  — show/hide toggle preference
'd1-last-score'            // e.g. '8/12'        — saved after each session
```

- Score is tracked in JS memory during the session (correct / total answered)
- On/off toggle button saves preference to localStorage
- Last score per domain is saved to localStorage when user finishes or leaves
- No cross-device sync — each browser is independent

## Page Structure (template)

Every `d#.html` must follow this layout:

```
┌─────────────────────────────────────────┐
│  ← Back to Study Guide    [☀/🌙] [📊]  │  ← top bar
├─────────────────────────────────────────┤
│  🤖 D1: Agentic Systems — Practice Q&A  │  ← page title
│  Score: 8 / 12  ✅                      │  ← score bar (toggleable)
├─────────────────────────────────────────┤
│  [Subdomain group heading]              │
│  Q: ...question...                      │
│  [ Show Answer ]                        │
│  A: ...answer... (hidden until clicked) │
│  [ ✅ Got it ] [ ❌ Review later ]       │
├─────────────────────────────────────────┤
│  ... more questions ...                 │
└─────────────────────────────────────────┘
```

## Building a New Domain Page

### Input
User provides a compiled Markdown file with Q&A pairs, grouped by subdomain.

### Steps
1. Read the MD file
2. Parse Q&A pairs — each question maps to one card
3. Group cards under subdomain `<section>` headings
4. Generate `practice/d#.html` using the standard template below
5. Update `index.html` sidebar — enable the dimmed link for that domain
6. Commit both files together

### Standard Template Checklist
- [ ] `data-theme="dark"` default (matches `index.html`)
- [ ] CSS variables match `index.html` (copy `:root` token block)
- [ ] Dark/light toggle wired to `localStorage` key `theme`
- [ ] Score toggle wired to `localStorage` key `practice-score-visible`
- [ ] Last score saved on `beforeunload` to `localStorage` key `d#-last-score`
- [ ] `← Back` button href is `../index.html`
- [ ] All questions collapsed by default (answer hidden)
- [ ] `✅ Got it` increments correct count; `❌ Review later` increments total only
- [ ] Responsive — readable on mobile

## Sidebar Entry in index.html

Each domain gets one `<a>` tag in the sidebar under a `📝 Practice` section:

```html
<!-- enabled -->
<a class="nav-item practice-link" href="practice/d1.html">🤖 D1 Practice</a>

<!-- disabled (file not built yet) -->
<span class="nav-item practice-link disabled">🔧 D2 Practice</span>
```

Disabled links are styled with `opacity: 0.4; cursor: not-allowed; pointer-events: none`.
Switch from `<span>` to `<a>` when the file is ready.

## Adding the Progress Dashboard (future)

When all 5 domains are built, create `practice/index.html` as a hub page:
- Reads `d1-last-score` through `d5-last-score` from localStorage
- Displays a summary card per domain with last score + link to re-attempt
- Accessible from `index.html` sidebar as `📊 My Progress`
