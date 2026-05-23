---
name: project-retro
description: Use at the end of every feature, sprint, or project. Captures what was accomplished, what is unfinished, what was surprising, and updates agents and skills with new knowledge. This is the primary mechanism for getting better with every project.
---

# Project Retrospective

Run this skill:
- At the end of every sprint or feature branch before merging
- At the end of a project before archiving or moving on
- When switching context for more than a few days

## Step 1 — What did we accomplish?

List every completed task as a bullet. Be specific — not "worked on booking" but "fixed seat count race condition with pessimistic lock on BookingRepository.findByIdForUpdate()".

If git is available:
```bash
git log --oneline main..HEAD
```

Use the commit history as the source of truth.

## Step 2 — What is unfinished?

List every task that was started but not completed. For each:
- What is it?
- Why is it unfinished? (blocked / deprioritised / harder than expected)
- What is the exact next step to resume it?

This becomes the opening context for the next session.

## Step 3 — What surprised us?

List every moment where the implementation diverged from the original plan. For each surprise:
- What did we expect?
- What actually happened?
- Should this become a `/capture-learning`? (If yes — run it now)

## Step 4 — What slowed us down?

Identify friction points: things that took longer than expected or required repeated effort.

Categories to check:
- **Process:** Had to explain the same context to Claude repeatedly → update CLAUDE.md or agents
- **Tooling:** A skill was missing or incomplete → update or create the skill
- **Knowledge:** Had to look up the same thing multiple times → it should be in an agent
- **Code:** A module was harder to change than it should be → flag as tech debt

## Step 5 — Update agents and skills

For each friction point identified in Step 4, make one of these changes:

| Problem | Fix |
|---------|-----|
| Claude forgot a project rule | Add it to the relevant agent `.md` |
| Claude kept asking about a pattern | Add an example to the relevant agent |
| A task had the same steps as last time | Convert it to a skill |
| A skill was missing a step | Update the skill's checklist |
| CLAUDE.md routing was wrong | Fix the skill routing table |

## Step 6 — Write the session summary

Create or update the last file in `memory/dispatch-logs/` with a human-readable summary:

```markdown
## Sprint / Feature: [name]
**Date:** YYYY-MM-DD
**Status:** Completed / Partially completed

### Accomplished
- [bullet list]

### Unfinished
- [bullet list with next steps]

### Key Decisions Made
- [any ADR-worthy decisions — if not already written, run /adr-retro]

### Learnings Captured
- [list any /capture-learning files written this sprint]

### Agent / Skill Updates
- [list any agents or skills updated]

### Next Session Starts With
[One sentence: the exact first task for next session]
```

## Step 7 — Set the next session's opening context

End by writing the opening prompt for the next session:

```
Next session: [exact task description]. Context: [one sentence on where we left off].
Files to read first: [CLAUDE.md + specific agent or ADR if relevant].
```

Save this in the dispatch log so the next session can start immediately.

## Checklist

- [ ] Completed tasks listed with specifics (not vague summaries)
- [ ] Unfinished tasks listed with exact next steps
- [ ] Every surprise assessed: should it be a `/capture-learning`?
- [ ] Every friction point resolved: agent updated, skill updated, or CLAUDE.md updated
- [ ] Dispatch log written with sprint summary
- [ ] Next session opening context written at the end of the dispatch log
- [ ] At least one agent or skill was updated this sprint (if nothing improved, something was missed)
