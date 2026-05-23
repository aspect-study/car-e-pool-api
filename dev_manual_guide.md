# AI-Augmented Project Structure — Developer Manual

A reference guide for setting up the `.claude/` + `memory/` pattern from scratch on any project.
Every example in this guide is drawn from the carpool API (Spring Boot 4, Telegram bot, MySQL 8, Flyway, MapStruct, JWT).

---

## What is this setup?

It is a structured way to make Claude Code a reliable, project-aware engineering partner rather than a generic assistant that needs re-explaining every session.

The setup has two parts:

**`.claude/`** — read by Claude Code automatically on every session. Contains:
- **Agents** — specialist personas with deep knowledge of one area of your stack
- **Skills** — step-by-step playbooks for tasks you repeat (add a migration, add a bot command)
- **Hooks** — bash scripts that run before/after Claude's tool calls as automated safety gates
- **Settings** — wiring file that connects hooks to events

**`memory/`** — the project's long-term knowledge store. Contains:
- **ADRs** — architecture decisions with context and consequences
- **PRDs** — product requirements that explain what the system is supposed to do
- **Learnings** — hard-won implementation gotchas captured as short files
- **Dispatch logs** — session logs auto-written at the end of each Claude Code session

Together they answer the question every AI assistant fails at: *"What does this specific project actually do, and what are the rules I must never break?"*

---

## Why should I use it?

Without this setup, every Claude Code session starts from zero. You re-explain the module structure, you re-explain why pessimistic locking is required, you re-explain that TINYINT maps to `Integer` not `byte`, you re-explain that Flyway migrations cannot be edited. Claude makes the same category of mistakes every time because it has no persistent project knowledge.

With this setup:

- **Hooks catch the most expensive mistakes before they happen.** The `flyway-gate.sh` hook physically blocks Claude from editing an existing migration file. You will never get a Flyway checksum failure from an AI edit. The `module-boundary-check.sh` hook blocks circular imports in Java before the file is even written.

- **Agents eliminate context re-explanation.** The `db-engineer` agent already knows TINYINT maps to `Integer`, knows the current highest migration is V44, knows the type mapping table for every MySQL type. You don't re-explain this — it reads the agent file.

- **Skills enforce consistency.** Every Flyway migration written via `/flyway-migration` follows the same steps: find max version, name the file, write safe SQL, sync the entity, update MapStruct. Not sometimes — every time.

- **Memory captures what code doesn't.** The code tells you *what* the system does. The ADRs tell you *why* it was built that way. When a new developer asks "why can't I use `@EventListener`?", ADR-003 has the full answer including the failure mode that motivated the decision.

- **Dispatch logs create a session diary.** At the end of every session, `vault-writeback.sh` writes a timestamped file with the git diff. Over time this becomes a lightweight changelog you didn't have to write manually.

The total setup time is 2–4 hours for a new project. The time saved per session for a non-trivial codebase is significant — particularly for the class of mistake that only manifests at runtime (wrong transaction phase, wrong type mapping, stale bot button NPE).

---

## Who is it for?

**Primary audience:** A senior developer or tech lead who owns a non-trivial backend project and uses Claude Code regularly. You already know your stack. You are not trying to learn Spring Boot from Claude — you are trying to make Claude a reliable pair programmer who knows your specific codebase.

**Also useful for:**
- Teams where multiple developers use Claude Code on the same project. Agents and skills standardize what Claude does, so different developers get consistent output.
- Solo developers working on a project with a lot of domain-specific rules (like this carpool project's plate masking rule, stale button classification, pessimistic locking requirement).
- Projects where the cost of certain mistakes is high — schema migrations that can't be undone, security misconfigurations, circular module dependencies.

**Not useful for:** One-off scripts, throwaway projects, or projects with no recurring patterns. The setup pays off when you have repeated tasks (add migration, add bot command, add REST endpoint) and non-obvious invariants that are easy to forget.

---

## When should I set this up?

### On a new project: set it up at the start of the first real feature sprint

Do it after you have:
- The module structure decided (even if not fully coded)
- At least one real pattern to document (e.g., "all DB changes go through Flyway")
- One recurring task identified (e.g., "I'll be adding REST endpoints regularly")

Do not wait until the project is large. The ADRs are cheapest to write when the decisions are fresh. The hooks are most valuable when you're moving fast and making mistakes.

### On an existing project: set it up as soon as you feel the repetition

Signs you need it:
- You've explained the same rule to Claude more than twice in one week
- Claude keeps getting the same thing wrong (wrong type mapping, wrong transaction phase, wrong migration naming)
- You've merged a Claude-assisted change that broke something that a checklist would have caught
- New developers (or Claude on a new session) keep asking "why is it done this way?"

For an existing project, the most valuable first steps are:
1. Write the hooks for your highest-risk areas first (migrations, module boundaries)
2. Write agents for the two most complex areas of your stack
3. Write ADRs for the two decisions that are hardest to explain to a new person

You don't need to do everything at once.

---

## Where does everything live and why?

```
project-root/
├── .claude/                  ← AI brain — Claude Code reads this every session
│   ├── agents/               ← Specialist personas
│   │   ├── architect.md
│   │   ├── java-backend.md
│   │   ├── db-engineer.md
│   │   ├── bot-engineer.md
│   │   ├── qa.md
│   │   ├── security.md
│   │   ├── concurrency.md
│   │   ├── frontend-web.md
│   │   └── flutter-mobile.md
│   ├── skills/               ← Step-by-step task playbooks
│   │   ├── flyway-migration/SKILL.md
│   │   ├── new-bot-command/SKILL.md
│   │   ├── new-event/SKILL.md
│   │   ├── new-scheduler/SKILL.md
│   │   ├── new-repository-method/SKILL.md
│   │   ├── new-bot-flow/SKILL.md
│   │   ├── add-rest-endpoint/SKILL.md
│   │   ├── commit-workflow/SKILL.md
│   │   ├── concurrency-review/SKILL.md
│   │   ├── spring-boot-review/SKILL.md
│   │   ├── pr-description/SKILL.md      ← Before every pull request
│   │   ├── security-review/SKILL.md     ← Before every merge to main
│   │   ├── capture-learning/SKILL.md    ← After any surprise or gotcha
│   │   ├── project-retro/SKILL.md       ← End of every sprint/feature
│   │   ├── adr-retro/SKILL.md           ← After every major feature
│   │   └── _manifest.yaml    ← Skill registry
│   ├── hooks/                ← Bash gate scripts
│   │   ├── guard-bash.sh
│   │   ├── flyway-gate.sh
│   │   ├── module-boundary-check.sh
│   │   └── vault-writeback.sh
│   ├── settings.json         ← Hook wiring (version-controlled)
│   └── settings.local.json   ← Personal permissions (not committed)
│
├── memory/                   ← Long-term project knowledge
│   ├── adrs/                 ← Architecture decision records
│   ├── prds/                 ← Product requirements
│   ├── dispatch-logs/        ← Auto-generated session logs
│   └── learnings/            ← Implementation gotchas
│
├── CLAUDE.md                 ← Root context file (always read by Claude)
└── [your source code]
```

**Why `.claude/` is at the project root:** Claude Code looks for this folder at the project root and loads it automatically. It must be here — not in `src/`, not in a subdirectory.

**Why `memory/` is also at the project root:** It's human-readable documentation that lives alongside the code. It's version-controlled so the team shares it. It's separate from `.claude/` because it's not instructions *to* Claude — it's knowledge *about* the project.

**Why `settings.json` is separate from `settings.local.json`:**
- `settings.json` contains the hooks — shared rules that apply to everyone. Commit this.
- `settings.local.json` contains your personal permission allowlist (which Bash commands, which WebFetch domains Claude can call without prompting). Don't commit this — it varies per developer and may contain paths specific to your machine.

**Why each skill is a folder (not a single file):** Each skill folder can grow to include examples, templates, or reference files alongside the `SKILL.md`. Starting as a folder means you never need to restructure later. The `_manifest.yaml` at the root of `skills/` is the index.

---

## How to set it up — step by step

### Prerequisites

- Claude Code CLI installed and authenticated
- A project with at least a basic structure (doesn't need to be large)
- `bash` available (Git Bash on Windows, native on Mac/Linux)
- `python3` available (used by hook scripts to parse JSON from stdin)

---

### Step 1 — Create the directory structure

```bash
mkdir -p .claude/agents
mkdir -p .claude/hooks
mkdir -p .claude/skills
mkdir -p memory/adrs
mkdir -p memory/prds
mkdir -p memory/dispatch-logs
mkdir -p memory/learnings
```

Nothing else yet. Just the folders.

---

### Step 2 — Write your CLAUDE.md (or update the existing one)

If you don't have a `CLAUDE.md` at the project root, create one. This is the single most important file — Claude reads it first on every session.

It must contain at minimum:
1. **How to build and run the project** — exact commands, not "run the app"
2. **Module/package structure** — what lives where and why
3. **Key constraints** — the non-obvious rules (e.g., "TINYINT maps to Integer", "never edit a Flyway migration")
4. **Skill routing** — which skill to invoke for which type of task

For the carpool project, `CLAUDE.md` documents the one-way module chain, the Flyway migration location, integration test requirements, and all skill routing rules. This file acts as the orientation document Claude reads before doing anything else.

**Tip:** Keep module-specific rules in each module's own `CLAUDE.md` (e.g., `carpool-bot/CLAUDE.md`). The root `CLAUDE.md` only covers what is shared across all modules.

---

### Step 3 — Write your agents

Create one `.md` file per specialist role in `.claude/agents/`. Each file uses this frontmatter:

```markdown
---
name: agent-name
description: One or two sentences describing WHEN to use this agent.
             This is what Claude reads to decide which agent to invoke.
---

# Agent Title

## Identity
What this agent is and what it owns.

## [Domain-Specific Sections]
Concrete patterns, code examples, checklists — all specific to YOUR project.
```

**Rules for writing good agents:**

- **The description must be a trigger condition**, not a job title. "Use when reviewing booking logic, adding service-layer methods, or debugging MapStruct compile errors" is useful. "Spring Boot expert" is not.
- **Include real code snippets from your project.** The `concurrency.md` agent includes the exact repository query pattern with `@Lock(LockModeType.PESSIMISTIC_WRITE)` that this project uses. Generic Spring Boot advice doesn't help.
- **Include checklists at the end of each agent.** These become the minimum bar Claude checks before finishing a task.
- **One agent per concern, not one per module.** `concurrency.md` covers a concern that cuts across `BookingService`, `NotificationService`, and every scheduler. `bot-engineer.md` covers the entire bot module. Match agents to areas of expertise, not to directory names.

**Agents for this project:**

| Agent | Owns | Invoke when |
|-------|------|-------------|
| `architect.md` | Cross-module planning | Planning any feature that touches >1 module |
| `java-backend.md` | `carpool-service` patterns | Writing service methods, DTOs, events, schedulers |
| `db-engineer.md` | MySQL, Flyway, JPA entities | Schema changes, migration writing, type mapping |
| `bot-engineer.md` | `carpool-bot` | Adding bot commands, flows, keyboards |
| `qa.md` | Tests at all layers | Writing tests, debugging test failures |
| `security.md` | JWT, OWASP, privacy | Auth changes, new endpoints, plate privacy |
| `concurrency.md` | Locking, transaction phases | Any write to ride/booking, notification timing |
| `frontend-web.md` | Next.js App Router web app | Building pages, API client, Telegram auth, JWT cookie, TypeScript types |
| `flutter-mobile.md` | Flutter mobile app | Building screens, Dio setup, Riverpod providers, go_router, freezed models |

---

### Step 4 — Write your hooks

Create bash scripts in `.claude/hooks/`. Each script:
- Reads the tool call JSON from stdin (Claude Code pipes it automatically)
- Exits 0 to allow the action
- Exits 2 to block the action (stderr is shown to Claude as an error message)

**Minimal hook template:**

```bash
#!/usr/bin/env bash
set -euo pipefail

INPUT=$(cat)

# Extract the relevant field from the tool input JSON
VALUE=$(echo "$INPUT" | python3 -c "
import json, sys
try:
    d = json.load(sys.stdin)
    print(d.get('tool_input', {}).get('YOUR_FIELD', ''))
except Exception:
    print('')
" 2>/dev/null || echo "")

# Check your condition
if echo "$VALUE" | grep -qE 'DANGEROUS_PATTERN'; then
    echo "BLOCKED: Explanation of what was blocked and what to do instead." >&2
    exit 2
fi

exit 0
```

**Tool input JSON structure by tool:**

```
Bash tool:   { "tool_name": "Bash",  "tool_input": { "command": "..." } }
Write tool:  { "tool_name": "Write", "tool_input": { "file_path": "...", "content": "..." } }
Edit tool:   { "tool_name": "Edit",  "tool_input": { "file_path": "...", "old_string": "...", "new_string": "..." } }
Stop event:  { "stop_reason": "end_turn" }
```

**Four hooks in this project:**

| Script | Triggers on | Blocks |
|--------|-------------|--------|
| `guard-bash.sh` | Every `Bash` call | `rm -rf`, `DROP TABLE`, `TRUNCATE`, force-push to protected branches |
| `flyway-gate.sh` | `Write` + `Edit` on any file | Editing existing migrations; writing migrations with wrong names |
| `module-boundary-check.sh` | `Write` + `Edit` on `.java` files | Imports that violate the one-way module dependency chain |
| `vault-writeback.sh` | `Stop` event | (doesn't block — creates session log in `memory/dispatch-logs/`) |

**Hook writing tips:**
- Block with a message that tells Claude what to do instead, not just what was blocked. "BLOCKED: Create V{N+1} instead" is actionable. "BLOCKED: Not allowed" is not.
- Use `grep -qiE` for case-insensitive pattern matching on SQL keywords.
- Keep hooks fast — they run on every matching tool call. Avoid network calls or file-system scans.
- Test hooks manually by piping JSON to them: `echo '{"tool_input":{"command":"rm -rf /"}}' | bash .claude/hooks/guard-bash.sh`

---

### Step 5 — Write settings.json to wire the hooks

Create `.claude/settings.json`:

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Bash",
        "hooks": [{ "type": "command", "command": "bash .claude/hooks/guard-bash.sh" }]
      },
      {
        "matcher": "Write",
        "hooks": [
          { "type": "command", "command": "bash .claude/hooks/flyway-gate.sh" },
          { "type": "command", "command": "bash .claude/hooks/module-boundary-check.sh" }
        ]
      },
      {
        "matcher": "Edit",
        "hooks": [
          { "type": "command", "command": "bash .claude/hooks/flyway-gate.sh" },
          { "type": "command", "command": "bash .claude/hooks/module-boundary-check.sh" }
        ]
      }
    ],
    "Stop": [
      {
        "hooks": [{ "type": "command", "command": "bash .claude/hooks/vault-writeback.sh" }]
      }
    ]
  }
}
```

**Hook event types:**
- `PreToolUse` — runs before the tool call executes. Exit 2 blocks it.
- `PostToolUse` — runs after the tool call. Exit code doesn't affect the result.
- `Stop` — runs when Claude is about to stop. Exit non-zero forces Claude to continue.

**Matcher values:**
`"Bash"`, `"Write"`, `"Edit"`, `"Read"`, `"Glob"`, `"Grep"`, `"WebFetch"`, `"WebSearch"`, `"Agent"`.
Use `""` (empty string) to match all tool calls.

Multiple hooks under the same matcher all run in order. If any exits 2, the tool call is blocked.

---

### Step 6 — Write your skills

Create a subfolder in `.claude/skills/` for each recurring task. The subfolder contains one file: `SKILL.md`.

**Skill file structure:**

```markdown
---
name: skill-name
description: One sentence: what does this skill help with? Used for routing.
---

# Skill Title

## Step 1 — [First Action]
Exact instructions, no ambiguity. Include file paths, class names, method signatures.

## Step 2 — [Second Action]
...

## Checklist
- [ ] Item that can be forgotten
- [ ] Item that can be forgotten
```

**What makes a good skill:**

- **Real file paths from your project.** `carpool-web/src/main/resources/db/migration/` not "your migrations folder".
- **Code examples from your project's actual patterns.** The `flyway-migration` skill shows the exact SQL patterns used in this project (TINYINT → INT, nullable vs. NOT NULL with DEFAULT).
- **A checklist at the end.** The checklist is the most valuable part — it captures what is easy to forget. For `flyway-migration`, the checklist includes "no existing migration file was touched" because that's the mistake that causes a runtime failure.
- **Numbered steps in implementation order.** Skills are not reference docs — they are playbooks. A developer (or Claude) follows them top-to-bottom.

**After writing each skill, add it to `_manifest.yaml`:**

```yaml
skills:
  - name: your-skill-name
    description: One sentence about what it does
    path: your-skill-name/SKILL.md
    triggers:
      - phrase that should invoke this skill
      - another trigger phrase
```

---

### Step 7 — Write your ADRs

Create one markdown file per significant architectural decision in `memory/adrs/`. Name them `ADR-NNN-short-description.md`.

**ADR template:**

```markdown
# ADR-NNN: Decision Title

**Status:** Accepted / Superseded / Deprecated
**Date:** YYYY-MM
**Deciders:** [who made this call]

## Context
What problem were you solving? What options existed?

## Decision
What did you choose and why?
Include code examples if the decision has a specific implementation pattern.

## Consequences
What must all future developers know as a result of this decision?
What are the failure modes if this decision is violated?
```

**What deserves an ADR:**

Write an ADR when:
- You chose option A over option B and the reason is not obvious from the code
- Violating the decision would cause a production incident (wrong transaction phase → notification for cancelled booking)
- A new developer would reasonably question why it was done this way

Do not write an ADR for every technical choice — only the ones that carry a constraint forward.

**ADRs in this project:**

| ADR | Decision | Consequence if violated |
|-----|----------|------------------------|
| ADR-001 | One-way module dependency chain | Circular dependency → Maven build failure |
| ADR-002 | Pessimistic locking for bookings | Missing lock → double-booking under concurrency |
| ADR-003 | `AFTER_COMMIT` listeners | Wrong phase → notification for rolled-back transaction |
| ADR-004 | Bot ignores group messages | Group commands → unintended side effects |
| ADR-005 | Plate masking at service layer | Missing mask → plate number visible in group chat |
| ADR-006 | Flyway-only schema changes | `ddl-auto=update` → Hibernate drops/alters production columns |
| ADR-007 | Stateless JWT + Telegram HMAC | Skipping `auth_date` check → replay attack possible |
| ADR-008 | Next.js App Router for web frontend | Pages Router patterns break App Router conventions |
| ADR-009 | Flutter over React Native for mobile | React Native patterns don't apply — different rendering model |
| ADR-010 | JWT in HTTP-only cookie on web | `localStorage` JWT → XSS-accessible token on any injected script |
| ADR-011 | `flutter_secure_storage` over `SharedPreferences` | `SharedPreferences` is plaintext on Android — readable on rooted devices |

---

### Step 8 — Seed your learnings

Create at least two files in `memory/learnings/` for the first non-obvious bugs you've encountered.

Format: `YYYY-MM-DD-short-description.md`

Each file: what happened, why it wasn't obvious, what the fix is, and which agent or skill now captures the rule.

These are the files that save the most time — not because the information is hard to find, but because you won't remember to look for it when you're three levels deep in a debugging session.

---

### Step 9 — Verify hooks are firing

Test each hook manually before trusting it:

```bash
# Test guard-bash.sh blocks rm -rf
echo '{"tool_name":"Bash","tool_input":{"command":"rm -rf target/"}}' \
  | bash .claude/hooks/guard-bash.sh
# Expected: exits 2, prints BLOCKED message to stderr

# Test flyway-gate.sh blocks bad migration name
echo '{"tool_name":"Write","tool_input":{"file_path":"db/migration/V45_bad-name.sql","content":""}}' \
  | bash .claude/hooks/flyway-gate.sh
# Expected: exits 2, prints BLOCKED message to stderr

# Test flyway-gate.sh allows correct migration name
echo '{"tool_name":"Write","tool_input":{"file_path":"db/migration/V45__add_booking_note.sql","content":""}}' \
  | bash .claude/hooks/flyway-gate.sh
# Expected: exits 0 (allowed), prints INFO message to stderr

# Test module-boundary-check.sh blocks illegal import
echo '{"tool_name":"Write","tool_input":{"file_path":"carpool-domain/Ride.java","content":"import com.carpool.service.BookingService;"}}' \
  | bash .claude/hooks/module-boundary-check.sh
# Expected: exits 2, prints BLOCKED message
```

Fix any hook that doesn't behave as expected before relying on it in a live session.

---

### Step 10 — Update CLAUDE.md with the structure reference

Add a section to your root `CLAUDE.md` that shows the `.claude/` and `memory/` structure and explains the skill routing. This ensures Claude always knows what's available, even without explicit instructions.

See the `## AI-Augmented Project Structure` section in this project's `CLAUDE.md` for the pattern.

---

## FAQ

### 1. How do hooks actually work under the hood?

When Claude Code is about to call a tool (e.g., `Write`), it first runs all `PreToolUse` hooks that match that tool name. Each hook script receives a JSON payload on stdin containing the tool name and the full tool input. The hook script processes it, then exits:
- **Exit 0** → Claude Code proceeds with the tool call
- **Exit 2** → Claude Code blocks the tool call and shows the hook's stderr output to Claude as an error message. Claude then re-plans without that action.

On `Stop` events, the hook runs after Claude has finished its turn. If the Stop hook exits non-zero, Claude is forced to continue (useful for "always check X before stopping" patterns).

---

### 2. When do agents activate?

Agents activate in two ways:
1. **You invoke one explicitly** — "Act as the db-engineer agent" or "use the concurrency agent to review this"
2. **Claude spawns one via the Agent tool** — when a task matches an agent's description trigger, Claude can spawn it as a subagent with its own context

The `description` field in each agent's frontmatter is the routing key. Write it as a trigger condition: "Use when reviewing booking logic..." — not a job title like "Spring Boot expert."

Agents do **not** activate automatically without being invoked. They are available, not automatic.

---

### 3. How do skills differ from agents?

| | Skills | Agents |
|---|--------|--------|
| **What** | Step-by-step playbooks for a specific task | Specialist personas with broad knowledge of a domain |
| **When** | You invoke them for a specific recurring task (`/flyway-migration`) | You invoke them for open-ended work in their domain |
| **Structure** | Numbered steps + checklist | System prompt + patterns + examples + checklists |
| **Example** | `/flyway-migration` → 6 steps to add a DB column | `db-engineer` agent → knows everything about MySQL + Flyway, answers questions, reviews code |

Think of it this way: the **skill** is the recipe. The **agent** is the chef who also knows the recipe and can improvise when something unexpected comes up.

---

### 4. What happens if I skip writing the hooks?

Nothing breaks immediately. But you lose the automated safety net. Without `flyway-gate.sh`, Claude can edit an existing migration (causing a Flyway checksum failure on next app start). Without `module-boundary-check.sh`, Claude can introduce a circular module dependency that only fails when Maven tries to resolve the build order. Without `guard-bash.sh`, Claude could run `rm -rf` on a directory you didn't intend to delete.

The hooks exist precisely because these mistakes are easy to make when moving fast. The cost of skipping them is a class of production-grade errors that are embarrassing and time-consuming to fix.

---

### 5. How do I write a good skill for my project?

A good skill has five properties:

1. **Specific file paths** — not "open the migration folder", but `carpool-web/src/main/resources/db/migration/`
2. **Real code patterns** — not "add a lock annotation", but the exact annotation + query pattern your project uses
3. **Ordered steps** — you follow them top-to-bottom, in the right sequence
4. **Covers the non-obvious** — the checklist at the end captures what is easy to forget (e.g., "no existing migration was modified")
5. **Failure consequences** — mention what happens if a step is skipped. This makes the checklist feel urgent, not bureaucratic.

Bad skill step: "Add the migration file."
Good skill step: "Create `V{N}__description.sql` in `carpool-web/src/main/resources/db/migration/`. Two underscores. All lowercase. Verify N is exactly one higher than the current maximum. Do not skip a number — Flyway will reject it."

---

### 6. How is `memory/` different from the CLAUDE.md files?

| | `CLAUDE.md` files | `memory/` |
|---|---|---|
| **Read when** | Every session, automatically | When you point Claude at it, or when reviewing decisions |
| **Contains** | Instructions and constraints for Claude's behavior | Project knowledge — decisions, requirements, lessons |
| **Updated** | When project conventions change | When decisions are made, bugs are fixed, sessions end |
| **Audience** | Claude (the AI) | Developers (and Claude when needed) |

`CLAUDE.md` is the rulebook. `memory/` is the history. Put in `CLAUDE.md` what must be followed every time. Put in `memory/` what needs to be understood occasionally.

---

### 7. How do I customize this for a different tech stack?

The structure is language and framework agnostic. The content of each file is what you adapt.

**For a Node.js/Express project:**
- Replace `db-engineer.md` with an agent that knows Prisma or Knex migrations
- Replace `flyway-gate.sh` with a hook that checks migration file naming in your chosen tool
- Replace `module-boundary-check.sh` with a hook that checks for circular `require()` paths
- Replace Spring Boot patterns in `java-backend.md` with Express/NestJS patterns

**For a Python/FastAPI project:**
- `db-engineer.md` → knows Alembic migration naming and revision IDs
- `flyway-gate.sh` → checks Alembic revision file naming
- Agents reference SQLAlchemy model patterns instead of JPA entity patterns

**The invariant is:** one agent per area of deep expertise, one skill per recurring task, hooks for your highest-risk operations, ADRs for your non-obvious architectural constraints. The names and content change. The pattern doesn't.

---

### 8. How do I add a new agent?

1. Create `.claude/agents/new-agent.md`
2. Write the frontmatter with `name` and `description` (description = trigger condition)
3. Write the body: identity section, real code patterns from your project, checklists
4. Reference it in `CLAUDE.md` under the skill routing section

Example: if you add a `rate-limiting.md` agent for a project with complex rate-limiting rules, add to `CLAUDE.md`: "Rate limiting review → invoke `rate-limiting` agent."

You don't need to register agents anywhere else. Claude Code discovers them from the `.claude/agents/` directory.

---

### 9. How do I test that hooks are actually firing during a Claude session?

Three ways:

**1. Manual test (pre-session):**
```bash
echo '{"tool_name":"Bash","tool_input":{"command":"rm -rf ."}}' \
  | bash .claude/hooks/guard-bash.sh
echo "Exit: $?"
```
Expected: exit code 2, BLOCKED message on stderr.

**2. Deliberate trigger in a session:**
Ask Claude to do something the hook blocks. For `guard-bash.sh`, ask: "run `rm -rf target/` to clean the build". Claude should report that the operation was blocked and suggest an alternative.

**3. Check vault-writeback.sh:**
At the end of any session, check `memory/dispatch-logs/` for a new timestamped file. If it's there, the Stop hook is firing.

If hooks aren't firing, the most common causes:
- `bash` is not in PATH (on Windows, use Git Bash or WSL)
- `python3` is not available (hooks use it to parse JSON)
- File permissions: `chmod +x .claude/hooks/*.sh` (on Mac/Linux)
- The `settings.json` path is wrong (must be `.claude/settings.json`, not inside `hooks/`)

---

### 10. Can hooks see the file content being written?

Yes. The `Write` tool's JSON payload includes `content` — the full text of the file being written. The `module-boundary-check.sh` hook reads this to scan for illegal import statements. The `Edit` tool's payload includes `old_string` and `new_string`.

This means you can write hooks that:
- Check the content of a Java file for hardcoded secrets before it's saved
- Verify a new controller method extracts `userId` from `Authentication`, not a request param
- Confirm a new `@Scheduled` method uses `fixedDelayString`, not a hardcoded int

---

### 11. How do ADRs stay useful over time?

They stay useful because they document the *why*, which the code doesn't. Even if the code changes, the constraint usually persists.

Practical tips:
- Add a **"Consequence if violated"** section — this is what makes an ADR actively useful rather than just historical. "If you use `@EventListener` instead of `@TransactionalEventListener(AFTER_COMMIT)`, the notification fires even when the booking transaction rolls back."
- Link ADRs from the relevant agent. The `concurrency.md` agent references ADR-002 and ADR-003.
- Mark superseded ADRs as `Status: Superseded by ADR-NNN`. Don't delete them — the history of why decisions changed is valuable.

---

### 12. What goes in `memory/learnings/` vs `memory/adrs/`?

| | `adrs/` | `learnings/` |
|---|---------|-------------|
| **What** | Deliberate architectural decisions | Unexpected bugs and non-obvious gotchas |
| **When written** | When a decision is made | When a bug is fixed that surprised you |
| **Tone** | Formal — context, decision, consequences | Informal — what happened, why it's non-obvious, fix |
| **Carpool example** | ADR-002: why pessimistic locking | Learning: TINYINT maps to Integer not byte |

The distinction: an ADR says "we chose X over Y and here's why". A learning says "we discovered X by hitting a wall and here's what the wall was."

---

### 13. Should `memory/` be committed to git?

Yes. All of `memory/` should be version-controlled. Reasons:

- ADRs are part of the project's technical documentation — they belong alongside the code
- Learnings are team knowledge — a new developer should find them when they onboard
- Dispatch logs are a lightweight changelog — useful for understanding the project's recent history
- PRDs define what the system is supposed to do — they belong in the repo, not in Confluence

The only exception: if dispatch logs become very large (many sessions per day), you might add `memory/dispatch-logs/` to `.gitignore` and treat them as local-only notes.

---

### 14. How do I handle a project where the tech stack is mixed (e.g., backend + frontend)?

Create separate agent files for each concern. This carpool project is a live example: it has a Spring Boot backend, a Telegram bot, an admin panel, a Next.js web app, and a Flutter mobile app — each with its own agent:

```
.claude/agents/
├── architect.md        — cross-cutting planning across all layers
├── java-backend.md     — Spring Boot service layer
├── db-engineer.md      — MySQL + Flyway
├── bot-engineer.md     — Telegram bot
├── security.md         — JWT on backend, HTTP-only cookies on web, secure storage on mobile
├── concurrency.md      — locking and transaction phases (backend concern)
├── frontend-web.md     — Next.js App Router, Telegram Login Widget, JWT cookie auth
└── flutter-mobile.md   — Flutter, Riverpod, Dio, go_router, flutter_secure_storage
```

Key pattern for multi-layer projects:
- **Security agent covers all layers** — JWT issuance (backend), cookie storage (web), `flutter_secure_storage` (mobile) belong in one agent so auth decisions are consistent
- **Each frontend has its own agent** — the Next.js patterns (Server Components, HTTP-only cookie, `middleware.ts`) are completely different from Flutter patterns (Riverpod, Dio interceptor, go_router redirect). Mixing them in one file makes both worse
- **Architect agent is the glue** — it knows how all layers connect: the API contract, which endpoints the frontend consumes, which DTOs the mobile app must model

Skills follow the same pattern — one skill per recurring task type, regardless of which layer. `add-rest-endpoint` for backend, `flyway-migration` for DB. Frontend skills (e.g., `add-next-page`, `add-flutter-screen`) can be added once patterns emerge from real development.

---

### 15. What breaks if I write a hook that exits 2 too aggressively?

Claude gets stuck. If a hook blocks a tool call that Claude needs to complete a task, Claude will either:
- Try a different approach (good — that's the intent)
- Get into a loop trying the same blocked action repeatedly
- Give up and report that it can't complete the task

If this happens, the fix is to make the hook more targeted. Instead of blocking all `rm` commands, block only `rm -rf`. Instead of blocking all edits to `.sql` files, block only edits to files inside `db/migration/`.

The principle: hooks should block the *specific dangerous pattern*, not the general category. Overly broad hooks make Claude less useful. Targeted hooks make it safer without reducing capability.

---

### 16. How do I update an agent or skill when project patterns change?

Edit the file directly. There's no registration or compilation step. The next Claude Code session picks up the updated file automatically.

For agents: update the patterns and examples when your codebase evolves. If you switch from MapStruct to a different mapper, update `java-backend.md`.

For skills: update the checklist when you discover a new failure mode. If you find that a new step is always needed (e.g., "update the OpenAPI spec when adding a REST endpoint"), add it to `add-rest-endpoint/SKILL.md`.

For hooks: update the blocked patterns when new risk areas emerge. If you add a new protected branch, add it to `guard-bash.sh`.

---

### 17. Is there a performance cost to running hooks on every tool call?

Yes, but it's minimal for well-written hooks. Each hook forks a bash process and runs in milliseconds. The `python3` JSON parsing in each hook adds ~50-100ms. On a modern machine this is imperceptible.

Where it can matter:
- Hooks that do file-system scans (e.g., `find . -name "*.java"`) — avoid these, they slow down every matching tool call
- Hooks that make network calls — never do this in a `PreToolUse` hook
- Hooks that run on very frequent tool calls (e.g., `Read`) — only add hooks to high-traffic tools if you have a specific reason

The four hooks in this project all run in well under 200ms. The `guard-bash.sh` and `flyway-gate.sh` hooks do only string matching — they're essentially instant.

---

## Quick Reference

One-liner for every file and folder in this setup.

### `.claude/`
| Path | What it is |
|------|-----------|
| `.claude/` | AI brain — Claude Code loads everything in here every session |
| `.claude/settings.json` | Wiring file — maps hook scripts to tool events (PreToolUse, Stop) |
| `.claude/settings.local.json` | Personal permissions allowlist — your Bash/WebFetch whitelist, not committed |

### `.claude/agents/`
| File | Job |
|------|-----|
| `agents/architect.md` | Plans features across the module chain; flags locking/events/migration needs |
| `agents/java-backend.md` | Spring Boot service layer — events, MapStruct, DTOs, pessimistic locking, plate masking |
| `agents/db-engineer.md` | MySQL + Flyway — migration naming, TINYINT→Integer rule, entity sync checklist |
| `agents/bot-engineer.md` | Telegram bot — BotContext, CallbackHandler, UserState flows, stale button guard |
| `agents/qa.md` | Tests-first — integration test patterns, concurrency tests, common failure fixes |
| `agents/security.md` | JWT + OWASP — Telegram HMAC, filter chain, plate privacy, endpoint auth review |
| `agents/concurrency.md` | Pessimistic locking, TransactionPhase, REQUIRES_NEW listener write-backs |
| `agents/frontend-web.md` | Next.js App Router — Telegram Login Widget, JWT HTTP-only cookie, all API types, LocalDateTime pitfall |
| `agents/flutter-mobile.md` | Flutter — Riverpod, Dio interceptors, go_router auth guard, freezed models, flutter_secure_storage |

### `.claude/skills/`
| Path | Job |
|------|-----|
| `skills/_manifest.yaml` | Registry of all skills with trigger phrases |
| `skills/new-bot-command/` | Steps to register a new Telegram callback action in CallbackHandler |
| `skills/new-bot-flow/` | Steps to add a multi-step conversation flow with UserState |
| `skills/new-event/` | Steps to add a domain event + TransactionalEventListener notification |
| `skills/flyway-migration/` | Steps to add a Flyway migration with entity + mapper sync |
| `skills/new-scheduler/` | Steps to add a `@Scheduled` background job |
| `skills/new-repository-method/` | Steps to add a Spring Data JPA query method |
| `skills/add-rest-endpoint/` | Steps to add a REST endpoint with JWT auth wiring |
| `skills/commit-workflow/` | Workflow rules — present diffs before editing, commit message only |
| `skills/concurrency-review/` | Audit: missing locks, wrong TransactionPhase, unsafe listener write-backs |
| `skills/spring-boot-review/` | Audit: filter chain order, endpoint rules, identity extraction, bean scope |
| `skills/pr-description/` | Structured PR title + 4-section description before every pull request |
| `skills/security-review/` | Full OWASP + project-specific checklist across backend, web, mobile before every merge |
| `skills/capture-learning/` | Write a learning entry + update agent after any surprise or non-obvious bug |
| `skills/project-retro/` | End-of-sprint retrospective — captures work done, updates agents and skills |
| `skills/adr-retro/` | Post-feature ADR review — writes missing decision records, updates agents |

### `.claude/hooks/`
| Script | Fires on | Blocks |
|--------|----------|--------|
| `hooks/guard-bash.sh` | Every `Bash` call | `rm -rf`, `DROP TABLE`, `TRUNCATE`, force-push to protected branches |
| `hooks/flyway-gate.sh` | `Write` + `Edit` | Editing existing migrations; wrong migration filename pattern |
| `hooks/module-boundary-check.sh` | `Write` + `Edit` (`.java` only) | Imports that violate the one-way module dependency chain |
| `hooks/vault-writeback.sh` | `Stop` event | Doesn't block — writes timestamped session log to `memory/dispatch-logs/` |

### `memory/`
| Path | What it contains |
|------|-----------------|
| `memory/adrs/` | Architecture Decision Records — why the system is built the way it is |
| `memory/adrs/ADR-001-*` | One-way module dependency chain and enforcement |
| `memory/adrs/ADR-002-*` | Pessimistic locking for booking seat decrement |
| `memory/adrs/ADR-003-*` | AFTER_COMMIT listeners for notifications (never @EventListener) |
| `memory/adrs/ADR-004-*` | Bot processes private chats only; group is announce-only |
| `memory/adrs/ADR-005-*` | Plate number masking at service layer — first 3 chars + *** |
| `memory/adrs/ADR-006-*` | Flyway is the only allowed schema change mechanism |
| `memory/adrs/ADR-007-*` | Stateless JWT auth + Telegram Login Widget HMAC validation |
| `memory/adrs/ADR-008-*` | Next.js App Router chosen for web frontend |
| `memory/adrs/ADR-009-*` | Flutter chosen over React Native for mobile |
| `memory/adrs/ADR-010-*` | JWT stored in HTTP-only cookie on web (not localStorage) |
| `memory/adrs/ADR-011-*` | flutter_secure_storage over SharedPreferences for mobile JWT |
| `memory/prds/` | Product requirement documents — what the system does and why |
| `memory/prds/prd-core-ride-booking-flow.md` | Full driver/passenger booking flow, constraints, entities, non-goals |
| `memory/dispatch-logs/` | Auto-generated session logs from vault-writeback.sh (git diff + notes template) |
| `memory/learnings/` | Hard-won implementation gotchas — non-obvious bugs with root causes and fixes |
| `memory/learnings/2025-05-23-tinyint-integer-type-rule.md` | TINYINT → Integer, not byte — Hibernate schema validation failure |
| `memory/learnings/2025-05-23-stale-button-session-recovery.md` | NPE when tapping old bot buttons after restart — UserState is null |
