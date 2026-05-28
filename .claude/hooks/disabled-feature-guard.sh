#!/usr/bin/env bash
# PreToolUse hook: when an Edit/Write targets a path that belongs to a feature
# marked DISABLED in CLAUDE.md (Bluetooth / live training, internal AI chat),
# print a reminder. Fails open — does not block the edit.

set -u

INPUT="$(cat || true)"
FILE_PATH="$(printf '%s' "$INPUT" | python3 -c 'import json,sys
try:
  d=json.load(sys.stdin)
  print(d.get("tool_input",{}).get("file_path",""))
except Exception:
  pass' 2>/dev/null || true)"

[[ -z "$FILE_PATH" ]] && exit 0

case "$FILE_PATH" in
  *bluetooth.service.ts \
  | *bluetooth-parsers.util.ts \
  | *workout-execution.service.ts \
  | *chat-sse.service.ts \
  | */ai/chat/* \
  | */ai/agents/* \
  | */api/ai/chat* )
    cat <<EOF
[disabled-feature-guard] $FILE_PATH is part of a DISABLED feature per CLAUDE.md
(Bluetooth/live training or internal AI chat). The MCP server is the only
AI surface still in scope. Confirm with the user before changing this file.
EOF
    ;;
esac

exit 0
