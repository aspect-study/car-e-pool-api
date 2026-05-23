---
name: pr-description
description: Use before every pull request. Produces a structured, honest PR description that reviewers can act on. Covers what changed and why, how to test it, what was left out, and any migration steps required.
---

# Write a PR Description

Run this skill every time you open a pull request, before pushing to GitHub.

## Step 1 — Collect the diff summary

Run:
```bash
git log main...HEAD --oneline
git diff main...HEAD --stat
```

Read the commit messages and changed files. Identify the intent — what user-visible or system-level outcome does this PR achieve?

## Step 2 — Write the title

Rules:
- Imperative mood: "Add", "Fix", "Remove", "Update" — not "Added" or "Adding"
- Under 72 characters
- No period at the end
- Specific enough to find in `git log` six months later — not "Fix bug" or "Update code"

Good examples:
- `Fix seat count race condition with pessimistic lock`
- `Add departure time editing with T-30 minute guard`
- `Remove stale button NPE on session restart`

## Step 3 — Write the description

Use this exact structure:

```markdown
## What changed and why
[2–4 sentences. Explain the intent, not the diff. Why does this PR exist?
What was broken or missing before? What is better now?]

## How to test manually
[Numbered steps. Be specific enough that someone who didn't write the code
can verify it works.]

1. [Step 1]
2. [Step 2]
3. Expected result: [what should happen]

Edge cases to verify:
- [Edge case 1]
- [Edge case 2]

## What was intentionally left out
[What did you decide NOT to do in this PR, and why?
If nothing, write "Nothing deferred — scope is complete."]

## Migration steps
[Any action the reviewer or deployer must take:
- DB migration to run
- Env variable to add
- Config to update
- Service to restart]

If none: "No migration required."

## Known rough edges
[Be honest. If there's a trade-off you made, a TODO you left, or a known
limitation in this implementation — say so here.]

If none: "No known rough edges."
```

## Step 4 — Check before submitting

Run these before the PR goes up:

- [ ] Does the title describe the outcome, not the method?
- [ ] Does "What changed and why" explain intent, not re-describe the diff?
- [ ] Are the test steps specific enough that someone else can follow them?
- [ ] Is anything deferred that should be tracked? (create a ticket / TODOS.md entry)
- [ ] Does the migration section cover everything needed for deployment?
- [ ] If there's a security change — has `/security-review` been run?
- [ ] If there's a new API endpoint — are TypeScript types and Flutter models updated?

## Checklist

- [ ] Title: imperative mood, under 72 chars, no period, specific
- [ ] Description: 4-section format (what+why, how to test, left out, migration)
- [ ] Test steps: numbered, specific, include edge cases
- [ ] Honest about trade-offs and known rough edges (not oversold)
- [ ] Migration section: complete and actionable
- [ ] Pre-submit checklist: all items checked
