#!/usr/bin/env bash
# Stop hook — runs mvn test-compile on the whole project after Claude finishes a turn.
# This fires once per turn (not per file), so all files are written before the check runs.
# Outputs a systemMessage to the terminal if there are errors; Claude will see it on the next turn.

# Run full project compile + test-compile in one pass
# -q suppresses success output; --no-transfer-progress suppresses download noise
OUTPUT=$(mvn test-compile -DskipTests --no-transfer-progress -q 2>&1)
EXIT_CODE=$?

if [ $EXIT_CODE -ne 0 ]; then
  # Show errors in terminal so user and Claude both see them
  MSG="COMPILATION ERRORS — fix before running tests:

$OUTPUT"

  bash -c "python3 -c \"
import json, sys
msg = sys.stdin.read()
print(json.dumps({'systemMessage': msg}))
\" <<< \"\$MSG\"" <<< "$MSG" 2>/dev/null || echo "{\"systemMessage\": \"Compilation failed — check terminal output for details.\"}"
fi

exit 0
