# Study Guide Redesign — Design Spec
**Date:** 2026-06-03
**File:** `study-guide.html`
**Audience:** Developer/student studying for the CCA-F exam

---

## Goals

- Replace the generic GitHub-dark dashboard aesthetic with a distinctive terminal/hacker look
- Preserve all existing content and component structure (no information loss)
- Add one meaningful study utility: mark-for-review with `localStorage` persistence
- Must work as a standalone HTML file on GitHub Pages (no build tools, no server)

---

## Visual Identity

### Palette

| Variable       | Value                    | Role                              |
|----------------|--------------------------|-----------------------------------|
| `--bg`         | `#000000`                | Pure black page background        |
| `--surface`    | `#0a0a0a`                | Sidebar, card surfaces            |
| `--border`     | `#1a1a1a`                | Structural borders                |
| `--accent`     | `#00FF41`                | Phosphor green — active/highlight |
| `--accent-dim` | `rgba(0,255,65,.08)`     | Tinted backgrounds                |
| `--text`       | `#00CC33`                | Body text (dimmed green)          |
| `--muted`      | `#336633`                | Secondary/label text              |
| `--code`       | `#050505`                | Code block background             |
| `--danger`     | `#FF3333`                | Error callouts                    |
| `--warn`       | `#FFB300`                | Warning callouts                  |
| `--info`       | `#00FFFF`                | Info callouts                     |
| `--purple`     | `#CC88FF`                | Wisdom callouts                   |

### Scanline Texture

`body::before` pseudo-element:
- `position: fixed`, full viewport, `pointer-events: none`, `z-index: 9999`
- `background: repeating-linear-gradient(transparent 0px, transparent 1px, rgba(0,0,0,0.04) 1px, rgba(0,0,0,0.04) 2px)`
- Creates a subtle CRT scanline effect without being distracting

### Typography

- `font-family: 'JetBrains Mono', 'Courier New', monospace` — applied globally to all content
- No sans-serif anywhere in the content pane
- Body `font-size: 14px`, `line-height: 1.7`

### Borders & Radius

- All structural elements (cards, callouts, accordions, domain header): `border-radius: 0`
- Small interactive elements (tags, buttons): `border-radius: 2px` max
- Sharp corners everywhere — this is non-negotiable for the terminal aesthetic

---

## Sidebar (File Tree)

Visual structure:
```
┌─ CCA-F STUDY TERMINAL ──────────┐
│ ~/car-e-pool-api/exam $          │
│                                  │
│ 📁 domains/                      │
│ ├── [★ 2] 01-architecture  18%  │
│ ├── [★ 0] 02-booking       22%  │
│ ├── [★ 1] 03-security      20%  │
│ ├── [★ 0] 04-bot           18%  │
│ └── [★ 0] 05-operations    22%  │
│                                  │
│ ── MARKED FOR REVIEW ──          │
│ ★ 3 items flagged                │
└──────────────────────────────────┘
```

### Details

- **Top bar:** Static fake prompt `~/car-e-pool-api/exam $` in `--muted`, non-interactive
- **Folder label:** `📁 domains/` as a section header above nav items
- **Nav items:** Prefixed with `├──` (middle items) or `└──` (last item)
- **Active item:** Prefixed with `>` replacing the tree character, full `--accent` color
- **Domain weight:** Right-aligned percentage on each nav item
- **Star badge:** `[★ N]` left of domain name, showing count of marked items in that domain; updates live
- **Footer section:** Replaces current footer with `── MARKED FOR REVIEW ──` header + total flagged count in accent green

---

## Main Content Pane

### Domain Header

Replaces gradient card with an ASCII-bordered block:
```
┌── DOMAIN 02: BOOKING & CONCURRENCY ────────────── [22%] ──┐
│  Description text here...                                   │
│                                                             │
│  ████████████████████░░░░░░░░░░░░  22% exam weight         │
└─────────────────────────────────────────────────────────────┘
```
- `border: 1px solid var(--accent)`, no radius
- Weight bar: `█` characters (filled) + `░` characters (empty), pure text
- Percentage label in `--accent`

### Cards

- Sharp corners, `border: 1px solid var(--border)`
- `h3` prefixed with `> ` in `--accent`

### Callouts

- Left border: `border-left: 3px solid <color>` (no radius)
- Background: 4% tint only
- Icons replaced with monospace labels: `[WARN]`, `[TIP]`, `[DANGER]`, `[INFO]`, `[★]`
- Label color matches callout type

### Accordions

- Toggle indicator: `[+]` (closed) / `[-]` (open) — replaces chevron
- Separator: `──────────────────` between header and body when open
- Note: only one accordion exists in the current file; mark-for-review is on cards, not accordions

### Code Blocks

- Label bar: filename in `--accent`, no background fill, just a bottom border
- Copy button: `[COPY]` / `[OK!]` text — no icon
- `border-radius: 0` on all code elements

### Flow Diagrams

- Steps connected with ` ──► ` in `--muted`
- No rounded step boxes

---

## Status Bar

Fixed bottom bar, full width, `height: 28px`, `z-index: 200`:

```
[ CCA-F STUDY TERMINAL ]  DOMAIN: 02-booking  ★ 3 FLAGGED  ──  [INSERT]  UTF-8  v2026
```

| Segment              | Content                          | Behavior       |
|----------------------|----------------------------------|----------------|
| Left                 | `[ CCA-F STUDY TERMINAL ]`       | Static         |
| Center-left          | `DOMAIN: <slug>`                 | Updates on nav |
| Center               | `★ N FLAGGED`                    | Live from localStorage |
| Right                | `[INSERT]  UTF-8  v2026`         | Static, decorative |

- Background: `#0a0a0a`, `border-top: 1px solid var(--accent)`
- All text `--muted` except `★ N FLAGGED` which is full `--accent`
- `main` gets `padding-bottom: 28px` to prevent content hiding behind it

---

## Mark-for-Review System

### Storage

- `localStorage` key: `ccaf_marked`
- Value: JSON array of element IDs (strings)
- Loaded on `DOMContentLoaded`, restored immediately before first paint

### Behavior

1. Each accordion has a stable `id` attribute (already exists or added)
2. `[MARK]` button click toggles the ID in the set, persists to `localStorage`
3. Button text: `[MARK]` → `[★]` when marked
4. Sidebar per-domain badge counts items whose IDs belong to that domain's section
5. Status bar total = `ccaf_marked.length`
6. All counts (sidebar badges + status bar) update synchronously on every toggle

### Scope

- Mark granularity: `.card` elements (not accordions — the file has only one accordion)
- Card IDs generated on `DOMContentLoaded`: `document.querySelectorAll('.card')` iterated by section, assigned `id="card-<sectionId>-<index>"` (e.g. `card-d1-03`)
- `[MARK]` button injected into each card's `h3` by the same JS loop
- No server dependency — works fully offline and on GitHub Pages

---

## Approach Selected

**Approach B** — Full retheme + status bar + fake prompt. No boot animation.

---

## Out of Scope

- Light mode toggle (current toggle removed or hidden — terminal mode is dark-only)
- Any new content sections
- External dependencies or build steps
