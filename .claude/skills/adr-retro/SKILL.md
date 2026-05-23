---
name: adr-retro
description: Use after completing any major feature or before merging a significant branch. Reviews implementation decisions made during the build, writes ADRs for anything not yet documented, and updates agents with new patterns.
---

# ADR Retrospective

Run this skill when a feature is complete and before the branch is merged.

## Step 1 — Review what decisions were made

Look at the git diff for this branch:
```bash
git diff main...HEAD --name-only
```

For each changed file, ask: "Was a non-obvious decision made here that a future developer would ask 'why was it done this way?'"

Check these categories:
- **Architecture:** Did we add a new module, introduce a new dependency, or change the module structure?
- **Database:** Did we make a schema choice that constrains future options? (e.g., chose ENUM over VARCHAR, chose nullable vs NOT NULL)
- **API design:** Did we make a contract decision that will be hard to change? (field names, response structure, error codes)
- **Auth / Security:** Did we make a choice about how identity is verified or how data is protected?
- **Performance:** Did we add a cache, change a query strategy, or introduce a background job for a reason?
- **Tech choice:** Did we choose one library/pattern over another for a specific reason?

## Step 2 — Check existing ADRs

Read `memory/adrs/` and list existing ADR numbers. Find the highest number — new ADRs start from N+1.

Check if any decision from Step 1 is already documented. If yes, verify the existing ADR is still accurate. If the implementation diverged from the ADR, update it.

## Step 3 — Write missing ADRs

For each undocumented decision identified in Step 1, create:
`memory/adrs/ADR-{NNN}-short-name.md`

Use three digits, zero-padded (ADR-012, ADR-013...).

File template:
```markdown
# ADR-{NNN}: [Short decision title]

**Status:** Accepted
**Date:** YYYY-MM-DD
**Deciders:** [developer name(s)]

## Context

[What problem were we solving? What options existed? What constraints applied?]

## Decision

[What did we choose, and why? Include code examples if the pattern is non-obvious.]

```[language]
// example showing the chosen approach
```

## Consequences

### Must always do
- [Rule 1 — what every future developer must know]
- [Rule 2]

### Will break if violated
- [What specific thing breaks if this ADR is ignored]

### Trade-offs accepted
- [What we knowingly gave up with this choice]
```

## Step 4 — Update agents with new patterns

For each new ADR written, update the relevant agent in `.claude/agents/`:

| ADR topic | Agent to update |
|-----------|----------------|
| Database / JPA / Flyway | `db-engineer.md` |
| Spring Boot / service layer | `java-backend.md` |
| Security / JWT / auth | `security.md` |
| Telegram bot | `bot-engineer.md` |
| Concurrency / locking | `concurrency.md` |
| Next.js / web | `frontend-web.md` |
| Flutter / mobile | `flutter-mobile.md` |
| Cross-cutting architecture | `architect.md` |

Add the key rule from the ADR to the agent's relevant section. Keep it short — one sentence rule plus a code snippet if needed.

## Step 5 — Update PRD if scope changed

Open `memory/prds/prd-[relevant].md`. If this feature changed what was originally planned:
- Update **Non-Goals** if something that was deferred is now built
- Update **Planned Extensions** if something new is now planned
- Update **Constraints** if a new rule was established

## Step 6 — Update ADR index

If this project has a Quick Reference table in `dev_manual_guide.md` or `Onboarding-Using-AI.md`, add the new ADRs to the table.

## Checklist

- [ ] Git diff reviewed for non-obvious decisions in all 6 categories
- [ ] Existing ADRs checked — none outdated by this feature
- [ ] New ADR files created for every undocumented decision (numbered correctly)
- [ ] Each ADR has: context, decision with code example, consequences, must-do rules, what breaks
- [ ] Relevant agents updated with the new rules from each ADR
- [ ] PRD updated if scope or constraints changed
- [ ] ADR Quick Reference table updated in docs if it exists
