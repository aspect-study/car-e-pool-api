#!/usr/bin/env bash
# Stop hook — appends a timestamped entry to today's dispatch log.
# One file per day instead of one file per stop event.
# Receives stop event JSON on stdin.

set -euo pipefail

INPUT=$(cat)
TIMESTAMP=$(date +"%H:%M")
DATE=$(date +"%Y-%m-%d")
LOG_DIR="memory/dispatch-logs"
LOG_FILE="$LOG_DIR/$DATE.md"

mkdir -p "$LOG_DIR"

STOP_REASON=$(echo "$INPUT" | python3 -c "
import json, sys
try:
    d = json.load(sys.stdin)
    print(d.get('stop_reason', 'end_turn'))
except Exception:
    print('end_turn')
" 2>/dev/null || echo "end_turn")

# Files changed in this session (committed or staged/unstaged)
COMMITTED=$(git diff --name-only HEAD 2>/dev/null | head -20 || echo "")
UNCOMMITTED=$(git status --short 2>/dev/null | grep -v "dispatch-logs" | head -20 || echo "")

# Create the day header if the file doesn't exist yet
if [ ! -f "$LOG_FILE" ]; then
    echo "# Dispatch Log — $DATE" > "$LOG_FILE"
    echo "" >> "$LOG_FILE"
fi

# Append this session's entry
cat >> "$LOG_FILE" <<EOF
## Session @ $TIMESTAMP — $STOP_REASON

### Files Modified
${COMMITTED:-"(no committed changes)"}

### Working Tree
${UNCOMMITTED:-"(clean)"}

---
EOF

touch "$LOG_DIR/.last_session"

exit 0