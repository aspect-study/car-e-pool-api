#!/usr/bin/env bash
# PreToolUse gate — checks for illegal cross-module imports in Java files.
# Enforces the carpool one-way module dependency chain.
# Receives tool call JSON on stdin. Exit 2 = block.
#
# Dependency chain (allowed direction: top → bottom only):
#   carpool-common → carpool-domain → carpool-repository → carpool-service
#   → carpool-bot / carpool-admin → carpool-web

set -euo pipefail

INPUT=$(cat)

FILE_PATH=$(echo "$INPUT" | python3 -c "
import json, sys
try:
    d = json.load(sys.stdin)
    print(d.get('tool_input', {}).get('file_path', ''))
except Exception:
    print('')
" 2>/dev/null || echo "")

CONTENT=$(echo "$INPUT" | python3 -c "
import json, sys
try:
    d = json.load(sys.stdin)
    print(d.get('tool_input', {}).get('content', ''))
except Exception:
    print('')
" 2>/dev/null || echo "")

# Block path traversal
if echo "$FILE_PATH" | grep -q '\.\.'; then
  echo "BLOCKED: Path traversal detected in file path: $FILE_PATH" >&2
  exit 2
fi

# Block sensitive files
if echo "$FILE_PATH" | grep -qE '(^|/)\.(env|git)(/|$)|\.pem$|\.key$|\.p12$|\.jks$|\.pkcs12$'; then
  echo "BLOCKED: Operation on sensitive file not allowed: $FILE_PATH" >&2
  exit 2
fi

# Only check Java files
if ! echo "$FILE_PATH" | grep -qE '\.java$'; then
  exit 0
fi

VIOLATIONS=""

# carpool-domain must NOT import from carpool-service, carpool-repository, carpool-bot, carpool-web, carpool-admin
if echo "$FILE_PATH" | grep -q "carpool-domain"; then
  if echo "$CONTENT" | grep -qE 'import com\.carpool\.(service|repository|bot|web|admin)\.'; then
    VIOLATIONS="carpool-domain imports from a downstream module (service/repository/bot/web/admin)"
  fi
fi

# carpool-common must NOT import from anything except java/jakarta standard libs
if echo "$FILE_PATH" | grep -q "carpool-common"; then
  if echo "$CONTENT" | grep -qE 'import com\.carpool\.(domain|service|repository|bot|web|admin)\.'; then
    VIOLATIONS="carpool-common imports from a downstream carpool module"
  fi
fi

# carpool-repository must NOT import from carpool-service, carpool-bot, carpool-web, carpool-admin
if echo "$FILE_PATH" | grep -q "carpool-repository"; then
  if echo "$CONTENT" | grep -qE 'import com\.carpool\.(service|bot|web|admin)\.'; then
    VIOLATIONS="carpool-repository imports from service/bot/web/admin (downstream module)"
  fi
fi

# carpool-service must NOT import from carpool-bot, carpool-web
if echo "$FILE_PATH" | grep -q "carpool-service"; then
  if echo "$CONTENT" | grep -qE 'import com\.carpool\.(bot|web)\.'; then
    VIOLATIONS="carpool-service imports from carpool-bot or carpool-web (downstream module)"
  fi
fi

# carpool-admin must NOT import from carpool-bot or carpool-web
if echo "$FILE_PATH" | grep -q "carpool-admin"; then
  if echo "$CONTENT" | grep -qE 'import com\.carpool\.(bot|web)\.'; then
    VIOLATIONS="carpool-admin imports from carpool-bot or carpool-web"
  fi
fi

if [ -n "$VIOLATIONS" ]; then
  echo "BLOCKED: Module boundary violation detected in $FILE_PATH" >&2
  echo "         Violation: $VIOLATIONS" >&2
  echo "" >&2
  echo "         Allowed dependency chain:" >&2
  echo "         common → domain → repository → service → bot/admin → web" >&2
  echo "" >&2
  echo "         If the downstream module needs functionality from an upstream one," >&2
  echo "         define a port interface in carpool-service and implement it in carpool-bot/carpool-web." >&2
  exit 2
fi

exit 0