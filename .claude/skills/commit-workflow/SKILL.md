---
name: commit-workflow
description: Use before making any file edits or commits in the carpool project. Enforces the required workflow: read CLAUDE.md first, present changes before editing, never run git commit or mvn builds.
---

# Carpool Project Workflow Rules

These rules apply in every session. They override default behavior.

## Before editing any file

1. Read `CLAUDE.md` at the project root first if not already done this session.
2. Present the planned changes to the user: which files will be edited and what will change.
3. Wait for explicit approval before making any edits.

## Never run these commands

```
git commit        ← user runs this themselves
git push          ← user runs this themselves
mvn clean install ← user runs builds themselves
mvn test          ← user runs tests themselves
mvn spring-boot:run ← user runs the app themselves
```

## Commit messages

When work is complete, output the commit message as plain text only. The user copies it and runs `git commit` and `git push` themselves — never use Bash or PowerShell for any git commit or push operation.

Format:

```
<type>: <short summary>

<optional body if the why isn't obvious>
```

Types: `feat`, `fix`, `refactor`, `chore`, `docs`, `test`.

Do not use `git commit` in a Bash tool call. Output the message as text only.

## File edit approval flow

When presenting planned changes:
- List each file path and a one-line summary of what changes
- If adding a migration: confirm the V-number is correct before writing
- If touching `CallbackHandler`: confirm the action name matches the pattern in use

Wait for the user to say "go ahead", "yes", "looks good", or equivalent before calling Edit or Write tools.
