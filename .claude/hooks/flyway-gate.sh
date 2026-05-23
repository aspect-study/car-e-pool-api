#!/usr/bin/env bash
# PreToolUse gate for Write/Edit tool calls targeting db/migration/.
# Enforces: correct naming pattern, blocks editing existing migrations.
# Receives tool call JSON on stdin. Exit 2 = block.

set -euo pipefail

INPUT=$(cat)

# Extract tool name and file path
TOOL_NAME=$(echo "$INPUT" | python3 -c "
import json, sys
try:
    d = json.load(sys.stdin)
    print(d.get('tool_name', ''))
except Exception:
    print('')
" 2>/dev/null || echo "")

FILE_PATH=$(echo "$INPUT" | python3 -c "
import json, sys
try:
    d = json.load(sys.stdin)
    print(d.get('tool_input', {}).get('file_path', ''))
except Exception:
    print('')
" 2>/dev/null || echo "")

# Only act on files inside db/migration/
if ! echo "$FILE_PATH" | grep -q "db/migration"; then
  exit 0
fi

FILENAME=$(basename "$FILE_PATH")

# Block editing an existing migration (Edit tool)
if [ "$TOOL_NAME" = "Edit" ]; then
  echo "BLOCKED: Never edit an existing Flyway migration — Flyway will reject the changed checksum on next startup." >&2
  echo "         Create a new migration: V{N+1}__description.sql where N = current highest version in db/migration/." >&2
  exit 2
fi

# For Write tool — enforce naming pattern V{N}__description.sql
if [ "$TOOL_NAME" = "Write" ]; then
  # Pattern: V<number>__<lowercase_snake_case>.sql
  if ! echo "$FILENAME" | grep -qE '^V[0-9]+__[a-z][a-z0-9_]+\.sql$'; then
    echo "BLOCKED: Migration filename '$FILENAME' does not match the required pattern." >&2
    echo "         Required: V{N}__short_description_in_snake_case.sql" >&2
    echo "         Examples: V45__add_user_bio_column.sql, V45__create_ride_ratings_table.sql" >&2
    echo "         Rules: two underscores, lowercase letters, underscores only (no hyphens, no spaces)." >&2
    exit 2
  fi

  # Warn about the current highest version (informational — Claude should verify)
  echo "INFO: Writing new migration '$FILENAME'. Verify this is exactly one higher than the current max in db/migration/. Current known max: V44." >&2
fi

exit 0