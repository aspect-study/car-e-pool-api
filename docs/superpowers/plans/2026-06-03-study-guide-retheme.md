# Study Guide Terminal Retheme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Retheme `study-guide.html` from GitHub-dark dashboard aesthetic to a distinctive terminal/hacker look with phosphor-green palette, CRT scanline, ASCII structural elements, and a mark-for-review system backed by `localStorage`.

**Architecture:** Single HTML file edit — CSS variables replaced in `<style>`, HTML structural elements updated in `<body>`, JS added/modified before `</body>`. No build tools, no external dependencies. Changes are sequential within the one file; each task produces a visually verifiable checkpoint.

**Tech Stack:** Vanilla HTML5, CSS3 (custom properties, pseudo-elements), Vanilla JS (localStorage, querySelectorAll, DOMContentLoaded). Verified by opening `file:///` in browser.

---

## File Structure

- **Modify:** `study-guide.html` (root of repo) — all changes in this one file
- **Spec:** `docs/superpowers/specs/2026-06-03-study-guide-redesign.md`

---

### Task 1: CSS Variables, Global Typography & Scanline Texture

**Files:**
- Modify: `study-guide.html` — `:root` block (line ~9), `body` rule, add `body::before`

- [ ] **Step 1: Replace `:root` CSS variables**

  Find the existing `:root{...}` block and replace it entirely with:

  ```css
  :root{
    --bg:#000000;
    --surface:#0a0a0a;
    --border:#1a1a1a;
    --accent:#00FF41;
    --accent-dim:rgba(0,255,65,.08);
    --text:#00CC33;
    --muted:#336633;
    --code:#050505;
    --danger:#FF3333;
    --warn:#FFB300;
    --info:#00FFFF;
    --purple:#CC88FF;
    --success:#00FF41;
    --sw:260px;
  }
  ```

- [ ] **Step 2: Delete the light-mode block**

  Remove the entire `[data-theme="light"]{...}` rule — terminal mode is dark-only.

- [ ] **Step 3: Update the `body` rule**

  Replace the `font-family` value and add font-size/line-height:

  ```css
  body{
    font-family:'JetBrains Mono','Courier New',monospace;
    background:var(--bg);
    color:var(--text);
    display:flex;
    min-height:100vh;
    font-size:14px;
    line-height:1.7;
  }
  ```

- [ ] **Step 4: Add CRT scanline texture**

  Add this rule immediately after the `body` rule:

  ```css
  body::before{
    content:'';
    position:fixed;
    inset:0;
    pointer-events:none;
    z-index:9999;
    background:repeating-linear-gradient(
      transparent 0px,
      transparent 1px,
      rgba(0,0,0,0.04) 1px,
      rgba(0,0,0,0.04) 2px
    );
  }
  ```

- [ ] **Step 5: Verify in browser**

  Open `study-guide.html` in a browser (`Ctrl+O` or drag to browser).
  Expected: Pure black background, phosphor green text (`#00CC33`), JetBrains Mono font throughout, subtle horizontal scanlines visible on solid backgrounds.

- [ ] **Step 6: Commit**

  ```bash
  git add study-guide.html
  git commit -m "style: apply terminal palette, mono font, CRT scanline to study guide"
  ```

---

### Task 2: Remove Border-Radius & Light Mode Toggle

**Files:**
- Modify: `study-guide.html` — CSS rules for `.card`, `.callout`, `.acc`, `.dom-hdr`, `.code-wrap`, `.code-lbl`, `pre`, `.tag`; HTML `mob-bar` toggle button; JS `toggleTheme` function

- [ ] **Step 1: Zero out border-radius on structural elements**

  In the CSS, update these rules to set `border-radius:0`:

  ```css
  .card{background:var(--card);border:1px solid var(--border);border-radius:0;padding:1.1rem 1.25rem}
  .callout{border-radius:0;padding:.9rem 1.1rem;margin:.9rem 0;display:flex;gap:.7rem;align-items:flex-start}
  .acc{border:1px solid var(--border);border-radius:0;overflow:hidden;margin-bottom:.9rem}
  .dom-hdr{background:var(--surface);border:1px solid var(--accent);border-radius:0;padding:1.4rem 1.5rem;margin-bottom:1.5rem}
  .code-wrap{position:relative;margin:.7rem 0}
  .code-lbl{display:flex;align-items:center;justify-content:space-between;background:transparent;border-bottom:1px solid var(--accent);padding:.3rem .9rem;border-radius:0;font-size:.72rem;color:var(--accent);font-weight:700;letter-spacing:.04em}
  pre{background:var(--code);border:1px solid var(--border);border-radius:0;padding:.9rem 1rem;overflow-x:auto;font-family:'JetBrains Mono','Courier New',monospace;font-size:.78rem;line-height:1.65;tab-size:2}
  .code-lbl+pre{border-top:none;border-radius:0}
  .tag{display:inline-block;padding:.18rem .55rem;border-radius:2px;font-size:.72rem;font-weight:700;margin:.15rem}
  ```

- [ ] **Step 2: Remove the theme toggle button from the mobile top bar**

  Find the `mob-bar` div and remove the toggle button:

  ```html
  <!-- BEFORE -->
  <div class="mob-bar">
    <button class="ham-btn" onclick="toggleSidebar()" aria-label="Open menu">☰</button>
    <span class="mob-title">CCA-F Blueprint</span>
    <button class="toggle-btn" onclick="toggleTheme()">🌙</button>
  </div>

  <!-- AFTER -->
  <div class="mob-bar">
    <button class="ham-btn" onclick="toggleSidebar()" aria-label="Open menu">☰</button>
    <span class="mob-title">CCA-F STUDY TERMINAL</span>
  </div>
  ```

- [ ] **Step 3: Remove `toggleTheme` function from JS**

  Find and delete the `function toggleTheme()` block from the `<script>` section.

- [ ] **Step 4: Verify in browser**

  Reload the page. Expected: All cards, callouts, accordions have sharp corners (no rounded corners). Mobile title reads "CCA-F STUDY TERMINAL". No theme toggle button visible.

- [ ] **Step 5: Commit**

  ```bash
  git add study-guide.html
  git commit -m "style: sharp corners everywhere, remove light mode toggle"
  ```

---

### Task 3: Sidebar Retheme — File Tree Structure

**Files:**
- Modify: `study-guide.html` — `.sidebar` CSS section; sidebar `<aside>` HTML

- [ ] **Step 1: Replace sidebar CSS**

  Replace the sidebar CSS block (`.sidebar`, `.sb-logo`, `.sb-nav`, `.nav-lbl`, `.nav-item`, `.nav-item .badge`, `.sb-footer`) with:

  ```css
  .sidebar{width:var(--sw);background:var(--surface);border-right:1px solid var(--border);position:fixed;top:0;left:0;height:100vh;overflow-y:auto;display:flex;flex-direction:column;z-index:100;transition:transform .25s ease}
  .sb-top{padding:.75rem 1rem .5rem;border-bottom:1px solid var(--border)}
  .sb-title{font-size:.8rem;font-weight:800;color:var(--accent);letter-spacing:.1em;text-transform:uppercase}
  .sb-prompt{font-size:.72rem;color:var(--muted);margin-top:.25rem;font-family:'JetBrains Mono','Courier New',monospace}
  .sb-nav{padding:.75rem .5rem;flex:1}
  .nav-folder{font-size:.72rem;color:var(--muted);padding:.5rem .5rem .3rem;letter-spacing:.04em}
  .nav-item{display:flex;align-items:center;gap:.4rem;padding:.38rem .5rem;cursor:pointer;font-size:.8rem;color:var(--muted);transition:color .1s;margin-bottom:.05rem;font-family:'JetBrains Mono','Courier New',monospace;white-space:nowrap;overflow:hidden}
  .nav-item:hover{color:var(--accent)}
  .nav-item.active{color:var(--accent)}
  .nav-tree{flex-shrink:0;color:var(--muted);width:2rem}
  .nav-item.active .nav-tree{color:var(--accent)}
  .nav-star{font-size:.72rem;color:var(--muted);margin-right:.25rem;flex-shrink:0}
  .nav-item.active .nav-star{color:var(--accent)}
  .nav-weight{margin-left:auto;font-size:.7rem;color:var(--muted);flex-shrink:0;padding-left:.5rem}
  .nav-item.active .nav-weight{color:var(--accent)}
  .sb-footer{padding:.75rem 1rem;border-top:1px solid var(--border)}
  .sb-footer-hdr{font-size:.68rem;color:var(--muted);letter-spacing:.06em;margin-bottom:.35rem}
  .sb-footer-count{font-size:.82rem;color:var(--accent);font-weight:700}
  ```

- [ ] **Step 2: Replace sidebar HTML**

  Replace the entire `<aside class="sidebar">` block with:

  ```html
  <aside class="sidebar" id="sidebar">
    <div class="sb-top">
      <div class="sb-title">CCA-F STUDY TERMINAL</div>
      <div class="sb-prompt">~/car-e-pool-api/exam $</div>
    </div>
    <nav class="sb-nav">
      <div class="nav-folder">📁 domains/</div>
      <div class="nav-item active" onclick="show('overview',this)" data-domain="overview">
        <span class="nav-tree">├──</span>
        <span class="nav-star" id="star-overview">[★ 0]</span>
        <span>overview</span>
        <span class="nav-weight"></span>
      </div>
      <div class="nav-item" onclick="show('d1',this)" data-domain="d1">
        <span class="nav-tree">├──</span>
        <span class="nav-star" id="star-d1">[★ 0]</span>
        <span>01-agentic</span>
        <span class="nav-weight">27%</span>
      </div>
      <div class="nav-item" onclick="show('d2',this)" data-domain="d2">
        <span class="nav-tree">├──</span>
        <span class="nav-star" id="star-d2">[★ 0]</span>
        <span>02-tools-mcp</span>
        <span class="nav-weight">18%</span>
      </div>
      <div class="nav-item" onclick="show('d3',this)" data-domain="d3">
        <span class="nav-tree">├──</span>
        <span class="nav-star" id="star-d3">[★ 0]</span>
        <span>03-claude-code</span>
        <span class="nav-weight">20%</span>
      </div>
      <div class="nav-item" onclick="show('d4',this)" data-domain="d4">
        <span class="nav-tree">├──</span>
        <span class="nav-star" id="star-d4">[★ 0]</span>
        <span>04-prompt-eng</span>
        <span class="nav-weight">20%</span>
      </div>
      <div class="nav-item" onclick="show('d5',this)" data-domain="d5">
        <span class="nav-tree">└──</span>
        <span class="nav-star" id="star-d5">[★ 0]</span>
        <span>05-responsible</span>
        <span class="nav-weight">15%</span>
      </div>
      <div class="nav-item" onclick="show('scenarios',this)" data-domain="scenarios">
        <span class="nav-tree">└──</span>
        <span class="nav-star" id="star-scenarios">[★ 0]</span>
        <span>06-scenarios</span>
        <span class="nav-weight"></span>
      </div>
    </nav>
    <div class="sb-footer">
      <div class="sb-footer-hdr">── MARKED FOR REVIEW ──</div>
      <div class="sb-footer-count"><span id="footer-count">★ 0</span> items flagged</div>
    </div>
  </aside>
  ```

  > **Note:** Verify the actual domain IDs and weights from the existing file — adjust `data-domain` values and percentages to match what `show()` currently uses.

- [ ] **Step 3: Verify in browser**

  Reload. Expected: Sidebar shows terminal header with fake prompt, tree characters (`├──`, `└──`), `[★ 0]` badges, domain weights right-aligned, and "MARKED FOR REVIEW" footer. Active item shows in `--accent` green.

- [ ] **Step 4: Commit**

  ```bash
  git add study-guide.html
  git commit -m "style: retheme sidebar to file-tree terminal structure"
  ```

---

### Task 4: Domain Header — ASCII Box Style

**Files:**
- Modify: `study-guide.html` — `.dom-hdr` CSS; each `.dom-hdr` HTML block (one per domain section)

- [ ] **Step 1: Replace `.dom-hdr` CSS**

  ```css
  .dom-hdr{border:1px solid var(--accent);border-radius:0;padding:1.2rem 1.5rem;margin-bottom:1.5rem;background:var(--surface)}
  .dom-hdr h2{font-size:1rem;font-weight:800;color:var(--accent);letter-spacing:.08em;text-transform:uppercase;margin-bottom:.5rem}
  .dom-hdr p{color:var(--muted);font-size:.83rem;line-height:1.65}
  .weight-row{display:flex;align-items:center;gap:.75rem;margin-top:.9rem}
  .weight-bar-text{color:var(--accent);font-size:.82rem;letter-spacing:.02em;font-family:'JetBrains Mono','Courier New',monospace}
  .weight-lbl{font-size:.82rem;font-weight:800;color:var(--accent);white-space:nowrap}
  ```

- [ ] **Step 2: Replace the weight bar HTML in each domain header**

  The existing `<div class="weight-bar"><div class="weight-fill" ...></div></div>` pattern must be replaced with a text-based bar. For a domain with weight W%, use `Math.round(W/100*20)` filled blocks.

  Example for D1 (27% → 5 filled of 20):
  ```html
  <!-- BEFORE -->
  <div class="weight-row">
    <div class="weight-bar"><div class="weight-fill" style="width:27%"></div></div>
    <span class="weight-lbl">27% exam weight</span>
  </div>

  <!-- AFTER -->
  <div class="weight-row">
    <span class="weight-bar-text">█████░░░░░░░░░░░░░░░</span>
    <span class="weight-lbl">27% exam weight</span>
  </div>
  ```

  Apply to all domain headers using the correct filled count per domain:
  - D1 27% → 5 filled: `█████░░░░░░░░░░░░░░░`
  - D2 18% → 4 filled: `████░░░░░░░░░░░░░░░░`
  - D3 20% → 4 filled: `████░░░░░░░░░░░░░░░░`
  - D4 20% → 4 filled: `████░░░░░░░░░░░░░░░░`
  - D5 15% → 3 filled: `███░░░░░░░░░░░░░░░░░`

- [ ] **Step 3: Verify in browser**

  Navigate to each domain. Expected: Domain header has accent-green border, UPPERCASE title, text block bar (`█░`) in green, no gradient, sharp corners.

- [ ] **Step 4: Commit**

  ```bash
  git add study-guide.html
  git commit -m "style: ASCII box domain headers with text weight bars"
  ```

---

### Task 5: Cards, Callouts & Flow Diagrams

**Files:**
- Modify: `study-guide.html` — CSS for `.card h3`, `.callout`, `.callout-icon`, `.flow`, `.flow-step`, `.flow-arrow`; HTML callout icon spans

- [ ] **Step 1: Add `> ` prefix to card headings via CSS**

  Add this rule:
  ```css
  .card h3::before{content:'> ';color:var(--accent)}
  .card h3{font-size:.88rem;font-weight:700;margin-bottom:.65rem;display:flex;align-items:center;gap:.4rem;color:var(--text)}
  ```

- [ ] **Step 2: Replace callout CSS**

  ```css
  .callout{border-radius:0;padding:.9rem 1.1rem;margin:.9rem 0;display:flex;gap:.7rem;align-items:flex-start}
  .callout-icon{font-size:.75rem;font-weight:800;flex-shrink:0;margin-top:.1rem;font-family:'JetBrains Mono','Courier New',monospace;letter-spacing:.04em}
  .callout h4{font-size:.82rem;font-weight:700;margin-bottom:.25rem}
  .callout p,.callout li{font-size:.82rem;line-height:1.6}
  .callout ul{padding-left:1rem;margin-top:.3rem}
  .callout.wisdom{background:rgba(204,136,255,.04);border:none;border-left:3px solid var(--purple)}.callout.wisdom h4{color:var(--purple)}.callout.wisdom .callout-icon{color:var(--purple)}
  .callout.tip{background:rgba(0,255,65,.04);border:none;border-left:3px solid var(--success)}.callout.tip h4{color:var(--success)}.callout.tip .callout-icon{color:var(--success)}
  .callout.warn{background:rgba(255,179,0,.04);border:none;border-left:3px solid var(--warn)}.callout.warn h4{color:var(--warn)}.callout.warn .callout-icon{color:var(--warn)}
  .callout.danger{background:rgba(255,51,51,.04);border:none;border-left:3px solid var(--danger)}.callout.danger h4{color:var(--danger)}.callout.danger .callout-icon{color:var(--danger)}
  .callout.info{background:rgba(0,255,255,.04);border:none;border-left:3px solid var(--info)}.callout.info h4{color:var(--info)}.callout.info .callout-icon{color:var(--info)}
  ```

- [ ] **Step 3: Replace callout icon HTML throughout the file**

  Find every `<span class="callout-icon">` and replace the emoji with the monospace label:
  - wisdom callouts: `[★]`
  - tip callouts: `[TIP]`
  - warn callouts: `[WARN]`
  - danger callouts: `[DANGER]`
  - info callouts: `[INFO]`

  Example:
  ```html
  <!-- BEFORE -->
  <div class="callout warn"><span class="callout-icon">⚠️</span>...

  <!-- AFTER -->
  <div class="callout warn"><span class="callout-icon">[WARN]</span>...
  ```

- [ ] **Step 4: Update flow diagram CSS**

  ```css
  .flow{display:flex;align-items:center;flex-wrap:wrap;gap:0;margin:.9rem 0;padding:.9rem;background:var(--code);border:1px solid var(--border);border-radius:0}
  .flow-step{background:var(--surface);border:1px solid var(--border);border-radius:0;padding:.35rem .65rem;font-size:.77rem;font-weight:600;white-space:nowrap}
  .flow-step.a{background:var(--accent-dim);border-color:var(--accent);color:var(--accent)}
  .flow-step.s{background:rgba(0,255,65,.08);border-color:var(--success);color:var(--success)}
  .flow-arrow{color:var(--muted);padding:0 .3rem;font-size:.85rem}
  ```

- [ ] **Step 5: Replace flow arrow HTML**

  Find all `<span class="flow-arrow">` elements and replace their content with ` ──► `:
  ```html
  <!-- BEFORE -->
  <span class="flow-arrow">→</span>

  <!-- AFTER -->
  <span class="flow-arrow"> ──► </span>
  ```

- [ ] **Step 6: Verify in browser**

  Expected: Card headings show `> ` in green. Callouts show `[WARN]`, `[TIP]` etc. labels, left border only, no rounded corners. Flow steps connected with ` ──► `.

- [ ] **Step 7: Commit**

  ```bash
  git add study-guide.html
  git commit -m "style: terminal callout labels, card h3 prefix, flow arrows"
  ```

---

### Task 6: Accordion & Code Block Retheme

**Files:**
- Modify: `study-guide.html` — `.acc-hdr`, `.acc-body` CSS; accordion HTML toggle indicator; `.copy-btn` CSS and JS; `.code-lbl` HTML

- [ ] **Step 1: Update accordion CSS**

  ```css
  .acc{border:1px solid var(--border);border-radius:0;overflow:hidden;margin-bottom:.9rem}
  .acc-hdr{background:var(--surface);padding:.85rem 1.1rem;cursor:pointer;display:flex;justify-content:space-between;align-items:center;font-weight:600;font-size:.87rem;transition:background .12s;user-select:none;color:var(--text)}
  .acc-hdr:hover{background:var(--accent-dim);color:var(--accent)}
  .acc-hdr .chev{transition:none;color:var(--accent);font-family:'JetBrains Mono','Courier New',monospace;font-size:.82rem}
  .acc-body{display:none;padding:1.1rem 1.25rem;background:var(--surface);border-top:1px solid var(--border)}
  .acc-body.open{display:block}
  ```

- [ ] **Step 2: Replace accordion chevron HTML**

  Find every `<span class="chev">` inside accordion headers and set its content to `[+]`. The JS that toggles it must set it to `[-]` when open.

  In the HTML:
  ```html
  <!-- BEFORE -->
  <span class="chev">▼</span>

  <!-- AFTER -->
  <span class="chev">[+]</span>
  ```

  In the JS `toggleAcc` function (or equivalent), change the chevron logic:
  ```js
  // Find the existing accordion toggle JS and update chevron text:
  const chev = hdr.querySelector('.chev');
  chev.textContent = hdr.classList.contains('open') ? '[-]' : '[+]';
  ```

- [ ] **Step 3: Update copy button CSS**

  ```css
  .copy-btn{background:transparent;border:1px solid var(--border);color:var(--muted);padding:.18rem .5rem;border-radius:0;cursor:pointer;font-size:.68rem;font-family:'JetBrains Mono','Courier New',monospace;transition:all .12s}
  .copy-btn:hover{background:var(--accent-dim);color:var(--accent);border-color:var(--accent)}
  .copy-btn.copied{background:rgba(0,255,65,.1);color:var(--success);border-color:var(--success)}
  ```

- [ ] **Step 4: Update copy button JS**

  Find the copy button click handler and update the text states:
  ```js
  // BEFORE: btn.textContent = 'Copied!';
  // AFTER:
  btn.textContent = '[OK!]';
  // ...and restore:
  btn.textContent = '[COPY]';
  ```

- [ ] **Step 5: Replace copy button HTML text throughout**

  Find all `<button class="copy-btn"` elements and set their text content to `[COPY]`:
  ```html
  <!-- BEFORE -->
  <button class="copy-btn" onclick="...">Copy</button>

  <!-- AFTER -->
  <button class="copy-btn" onclick="...">[COPY]</button>
  ```

- [ ] **Step 6: Verify in browser**

  Expected: Accordion headers show `[+]` toggle, clicking opens body and shows `[-]`. Copy buttons show `[COPY]`, flash `[OK!]` on click. No rounded corners anywhere.

- [ ] **Step 7: Commit**

  ```bash
  git add study-guide.html
  git commit -m "style: [+]/[-] accordion toggle, [COPY]/[OK!] buttons"
  ```

---

### Task 7: Status Bar

**Files:**
- Modify: `study-guide.html` — add CSS for `.status-bar`; add HTML element before `</body>`; update `show()` JS function; add `padding-bottom` to `.main`

- [ ] **Step 1: Add status bar CSS**

  Add this rule in the `<style>` block:
  ```css
  .status-bar{position:fixed;bottom:0;left:0;right:0;height:28px;background:#0a0a0a;border-top:1px solid var(--accent);display:flex;align-items:center;padding:0 1rem;gap:1.5rem;z-index:200;font-size:.68rem;font-family:'JetBrains Mono','Courier New',monospace;color:var(--muted)}
  .status-bar .sb-left{font-weight:800;color:var(--muted)}
  .status-bar .sb-domain{color:var(--muted)}
  .status-bar .sb-flagged{color:var(--accent);font-weight:800}
  .status-bar .sb-right{margin-left:auto;color:var(--muted)}
  ```

- [ ] **Step 2: Add `padding-bottom` to `.main`**

  Update the `.main` rule to add `padding-bottom:28px`:
  ```css
  .main{margin-left:var(--sw);flex:1;padding:2rem;padding-bottom:28px;max-width:calc(100vw - var(--sw))}
  ```

- [ ] **Step 3: Add status bar HTML**

  Add this immediately before `</body>`:
  ```html
  <div class="status-bar">
    <span class="sb-left">[ CCA-F STUDY TERMINAL ]</span>
    <span class="sb-domain">DOMAIN: <span id="sb-domain-slug">overview</span></span>
    <span class="sb-flagged">★ <span id="sb-flag-count">0</span> FLAGGED</span>
    <span class="sb-right">──  [INSERT]  UTF-8  v2026</span>
  </div>
  ```

- [ ] **Step 4: Wire status bar domain slug to `show()` function**

  Find the existing `show(id, el)` function and add one line to update the domain slug:
  ```js
  function show(id, el) {
    // ... existing logic (hide sections, remove active class, show new section) ...
    document.getElementById('sb-domain-slug').textContent = id; // ADD THIS LINE
  }
  ```

- [ ] **Step 5: Verify in browser**

  Expected: Fixed green-bordered bar at bottom of viewport. Shows `[ CCA-F STUDY TERMINAL ]`, current domain slug updates when navigating, `★ 0 FLAGGED` in accent green. Content doesn't hide behind bar.

- [ ] **Step 6: Commit**

  ```bash
  git add study-guide.html
  git commit -m "feat: add terminal status bar with domain slug and flag count"
  ```

---

### Task 8: Mark-for-Review System

**Files:**
- Modify: `study-guide.html` — add CSS for `.mark-btn`; add JS for card ID generation, MARK button injection, localStorage persistence, badge updates

- [ ] **Step 1: Add mark button CSS**

  Add in the `<style>` block:
  ```css
  .mark-btn{background:transparent;border:1px solid var(--border);color:var(--muted);padding:.1rem .38rem;border-radius:0;cursor:pointer;font-size:.65rem;font-family:'JetBrains Mono','Courier New',monospace;margin-left:auto;flex-shrink:0;transition:all .12s}
  .mark-btn:hover{border-color:var(--accent);color:var(--accent)}
  .mark-btn.marked{border-color:var(--accent);color:var(--accent);background:var(--accent-dim)}
  ```

- [ ] **Step 2: Add the mark-for-review JS**

  Add this script block immediately before `</body>` (after the status bar HTML, after any existing `<script>` block):

  ```js
  <script>
  (function() {
    const STORAGE_KEY = 'ccaf_marked';

    function loadMarked() {
      try { return new Set(JSON.parse(localStorage.getItem(STORAGE_KEY)) || []); }
      catch(e) { return new Set(); }
    }

    function saveMarked(set) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify([...set]));
    }

    function updateCounts(marked) {
      // Update status bar total
      document.getElementById('sb-flag-count').textContent = marked.size;
      document.getElementById('footer-count').textContent = '★ ' + marked.size;

      // Update per-domain sidebar badges
      const sections = document.querySelectorAll('.section');
      sections.forEach(function(section) {
        const sectionId = section.id;
        const starEl = document.getElementById('star-' + sectionId);
        if (!starEl) return;
        const cards = section.querySelectorAll('.card');
        let count = 0;
        cards.forEach(function(card) { if (marked.has(card.id)) count++; });
        starEl.textContent = '[★ ' + count + ']';
      });
    }

    function toggleMark(cardId, btn, marked) {
      if (marked.has(cardId)) {
        marked.delete(cardId);
        btn.textContent = '[MARK]';
        btn.classList.remove('marked');
      } else {
        marked.add(cardId);
        btn.textContent = '[★]';
        btn.classList.add('marked');
      }
      saveMarked(marked);
      updateCounts(marked);
    }

    document.addEventListener('DOMContentLoaded', function() {
      const marked = loadMarked();

      // Assign IDs to all cards and inject MARK buttons
      const sections = document.querySelectorAll('.section');
      sections.forEach(function(section) {
        const sectionId = section.id;
        const cards = section.querySelectorAll('.card');
        cards.forEach(function(card, index) {
          const cardId = 'card-' + sectionId + '-' + String(index).padStart(2, '0');
          card.id = cardId;

          // Inject MARK button into h3
          const h3 = card.querySelector('h3');
          if (h3) {
            const btn = document.createElement('button');
            btn.className = 'mark-btn' + (marked.has(cardId) ? ' marked' : '');
            btn.textContent = marked.has(cardId) ? '[★]' : '[MARK]';
            btn.addEventListener('click', function(e) {
              e.stopPropagation();
              toggleMark(cardId, btn, marked);
            });
            h3.appendChild(btn);
          }
        });
      });

      // Restore counts from localStorage
      updateCounts(marked);
    });
  })();
  </script>
  ```

- [ ] **Step 3: Verify mark-for-review in browser**

  Open the page. Expected:
  - Every card's `h3` has a `[MARK]` button at the right edge
  - Clicking `[MARK]` toggles it to `[★]`, button turns accent green
  - Status bar `★ N FLAGGED` increments live
  - Sidebar `[★ N]` badge for the active domain increments live
  - Footer `★ N items flagged` increments live
  - Reload the page — marked cards restore correctly from localStorage
  - Marked cards show `[★]` on reload without flickering

- [ ] **Step 4: Commit**

  ```bash
  git add study-guide.html
  git commit -m "feat: mark-for-review system with localStorage, live sidebar badges, status bar count"
  ```

---

## Self-Review Checklist

- [x] **CSS Variables** — all 12 palette vars + `--sw` covered in Task 1
- [x] **Scanline** — `body::before` in Task 1
- [x] **Typography** — JetBrains Mono global in Task 1
- [x] **Border-radius: 0** — structural elements in Task 2
- [x] **Light mode removed** — Task 2
- [x] **Sidebar file tree** — tree chars, star badges, fake prompt, footer in Task 3
- [x] **Domain header ASCII box** — accent border, text weight bar in Task 4
- [x] **Card `> ` prefix** — CSS `::before` in Task 5
- [x] **Callout monospace labels** — `[WARN]` etc. in Task 5
- [x] **Callout left-border only** — no radius, 4% tint in Task 5
- [x] **Flow `──►`** — Task 5
- [x] **Accordion `[+]`/`[-]`** — Task 6
- [x] **`[COPY]`/`[OK!]` buttons** — Task 6
- [x] **Status bar** — fixed bottom, domain slug, flag count in Task 7
- [x] **Mark-for-review** — card IDs, MARK button injection, localStorage, live counts in Task 8

---

## Execution Options

Plan complete and saved to `docs/superpowers/plans/2026-06-03-study-guide-retheme.md`.

**1. Subagent-Driven (recommended)** — fresh subagent per task, two-stage review between tasks, fast iteration

**2. Inline Execution** — execute tasks in this session using executing-plans, checkpoints for review

Which approach?
