#!/usr/bin/env bash
# PreToolUse gate — blocks destructive shell commands before they execute.
# Receives tool call JSON on stdin. Exit 2 = block + show message to Claude.

set -euo pipefail

INPUT=$(cat)

# Extract the command string from the tool input JSON
COMMAND=$(echo "$INPUT" | python3 -c "
import json, sys
try:
    d = json.load(sys.stdin)
    print(d.get('tool_input', {}).get('command', ''))
except Exception:
    print('')
" 2>/dev/null || echo "")

if [ -z "$COMMAND" ]; then
  exit 0
fi

# ── Hard blocks ───────────────────────────────────────────────────────────────

# Block recursive force-delete
if echo "$COMMAND" | grep -qE '\brm\s+(-[a-zA-Z]*f[a-zA-Z]*r[a-zA-Z]*|-[a-zA-Z]*r[a-zA-Z]*f[a-zA-Z]*)\b'; then
  echo "BLOCKED: 'rm -rf' is not allowed. Use targeted file removal instead." >&2
  exit 2
fi

# Block DDL DROP operations (should go through Flyway)
if echo "$COMMAND" | grep -qiE '\bDROP\s+(TABLE|DATABASE|SCHEMA|INDEX)\b'; then
  echo "BLOCKED: DDL DROP must go through a Flyway migration (V{N}__description.sql in carpool-web/src/main/resources/db/migration/). Run /flyway-migration skill." >&2
  exit 2
fi

# Block TRUNCATE (data loss, not a migration)
if echo "$COMMAND" | grep -qiE '\bTRUNCATE\s+'; then
  echo "BLOCKED: TRUNCATE detected. For test data cleanup, use @BeforeEach with DELETE in integration tests. For schema changes, use Flyway." >&2
  exit 2
fi

# Block force-push to protected branches
if echo "$COMMAND" | grep -qE 'git push.*--force.*\b(main|master|development)\b|git push.*\b(main|master|development)\b.*--force'; then
  echo "BLOCKED: Force-push to a protected branch (main/master/development) is not allowed." >&2
  exit 2
fi

# Block deleting all git history
if echo "$COMMAND" | grep -qE 'git reset --hard HEAD~[0-9]{2,}'; then
  echo "BLOCKED: Resetting more than 9 commits back is risky. Confirm the exact commit range before proceeding." >&2
  exit 2
fi

# Warn (but allow) — editing an existing Flyway migration
if echo "$COMMAND" | grep -qE 'db/migration/V[0-9]+__.*\.sql'; then
  if echo "$COMMAND" | grep -qE '(sed|awk|echo|cat >|>>)'; then
    echo "WARNING: Modifying an existing Flyway migration will cause a checksum failure on next startup. Create V{N+1} instead." >&2
    # Exit 2 to block; change to exit 0 to warn-only
    exit 2
  fi
fi

exit 0