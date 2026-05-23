#!/usr/bin/env bash
# Stop hook — creates a timestamped dispatch log entry when Claude finishes a session.
# Records: date, files changed, what was done. User can annotate with learnings.
# Receives stop event JSON on stdin.

set -euo pipefail

INPUT=$(cat)
TIMESTAMP=$(date +"%Y-%m-%d_%H-%M")
DATE=$(date +"%Y-%m-%d")
LOG_DIR="memory/dispatch-logs"
LOG_FILE="$LOG_DIR/$TIMESTAMP.md"

# Ensure directory exists
mkdir -p "$LOG_DIR"

# Extract stop reason if present
STOP_REASON=$(echo "$INPUT" | python3 -c "
import json, sys
try:
    d = json.load(sys.stdin)
    print(d.get('stop_reason', 'end_turn'))
except Exception:
    print('end_turn')
" 2>/dev/null || echo "end_turn")

# Collect recently modified files (last 10 minutes, in the project)
CHANGED_FILES=$(find . -name "*.java" -o -name "*.sql" -o -name "*.properties" -o -name "*.yaml" -o -name "*.yml" -o -name "*.json" \
  | grep -v ".git/" | grep -v "target/" \
  | xargs ls -t 2>/dev/null | head -20 \
  | xargs -I{} find {} -newer "$LOG_DIR/.last_session" 2>/dev/null \
  || echo "(unable to determine changed files)")

# Write the log entry
cat > "$LOG_FILE" <<EOF
# Session Log — $DATE

## Stop Reason
$STOP_REASON

## Files Modified This Session
$(git diff --name-only HEAD 2>/dev/null | head -30 || echo "(git not available or no changes)")

## Staged / Unstaged Changes
$(git status --short 2>/dev/null | head -20 || echo "(git not available)")

## Notes
<!-- Fill in what was done, decisions made, and any learnings worth keeping. -->
<!-- Move notable patterns to memory/learnings/ if they should persist across sessions. -->

EOF

# Update the last-session marker
touch "$LOG_DIR/.last_session"

exit 0