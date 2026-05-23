---
name: capture-learning
description: Use after any surprise, unexpected bug, gotcha, or non-obvious discovery during development. Writes a learning entry to memory/learnings/ and updates the relevant agent with the new rule so it applies from the next session onward.
---

# Capture a Learning

Run this skill any time:
- A bug took longer than 30 minutes to diagnose
- The fix was non-obvious (not just a typo or missing import)
- A framework, library, or tool behaved unexpectedly
- You had to abandon your original approach and start again
- You discovered a rule that must always be followed in this project

## Step 1 — Gather the facts while they are fresh

Answer these four questions:

1. **What happened?** One sentence describing the symptom or wrong assumption.
2. **Why was it surprising?** What would a reasonable developer have expected instead?
3. **What is the fix or correct approach?** The exact rule, with a code example if it helps.
4. **Where in the codebase is this relevant?** Module, class, or file path.

## Step 2 — Check if a learning already exists

Read `memory/learnings/README.md`. If the issue is already documented, update the existing file instead of creating a new one.

## Step 3 — Create the learning file

File path: `memory/learnings/YYYY-MM-DD-short-description.md`

Use today's date. Use kebab-case for the description. Examples:
- `2026-05-23-tinyint-integer-type-rule.md`
- `2026-06-01-transactional-event-listener-readOnly-conflict.md`
- `2026-06-15-flutter-ios-keychain-persists-after-uninstall.md`

File format:
```markdown
# [Short title]

**Date:** YYYY-MM-DD
**Layer:** [Backend / Web / Mobile / Infra]
**Symptom:** [One sentence — what happened]

## What Went Wrong

[2–4 sentences describing the problem and why it was non-obvious]

## The Fix

[The correct approach, with a code example if applicable]

## The Rule

**[Bold one-sentence rule that future developers (and Claude) should follow]**

## Why It Matters

[One sentence on what breaks if the rule is not followed]
```

## Step 4 — Update the relevant agent

Identify which `.claude/agents/` file owns this domain:
- Database / JPA / Flyway → `db-engineer.md`
- Spring Boot service layer / events / schedulers → `java-backend.md`
- JWT / security / auth → `security.md`
- Telegram bot flows / callbacks → `bot-engineer.md`
- Concurrency / locking / transactions → `concurrency.md`
- Next.js / web frontend → `frontend-web.md`
- Flutter / mobile → `flutter-mobile.md`

Open the agent file and add the rule to the relevant section. Write it as a rule, not a story:

```markdown
**[RULE]** [The rule in one sentence]
*(Reason: [why this exists — the surprise that caused it])*
```

## Step 5 — Update README index

Open `memory/learnings/README.md` and add a line to the correct section:

```markdown
- [YYYY-MM-DD-description.md](YYYY-MM-DD-description.md) — one-sentence summary
```

## Checklist

- [ ] Learning file created in `memory/learnings/` with correct date prefix
- [ ] File contains: symptom, what went wrong, the fix, the rule, why it matters
- [ ] Relevant agent updated with the new rule
- [ ] `memory/learnings/README.md` index updated
- [ ] Rule is written as a command ("always X", "never Y"), not a story
