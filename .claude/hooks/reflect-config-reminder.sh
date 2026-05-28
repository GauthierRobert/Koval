#!/usr/bin/env bash
# PostToolUse hook: when an Edit/Write touches a Java file under ai/tools/ or mcp/,
# warn if reflect-config.json doesn't reference the affected class.
# Fails open — never blocks the edit, just prints a reminder.

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
  */backend/src/main/java/com/koval/trainingplannerbackend/ai/tools/*.java|*/backend/src/main/java/com/koval/trainingplannerbackend/mcp/*.java)
    ;;
  *)
    exit 0
    ;;
esac

# Derive the FQN from the path
FQN="$(printf '%s' "$FILE_PATH" \
  | sed -E 's#^.*/backend/src/main/java/##; s#\.java$##; s#/#.#g')"
[[ -z "$FQN" ]] && exit 0

CONFIG="$(find backend/src/main/resources/META-INF/native-image -name reflect-config.json 2>/dev/null | head -n1)"
[[ -z "$CONFIG" ]] && exit 0

if ! grep -q "\"$FQN\"" "$CONFIG" 2>/dev/null; then
  cat <<EOF
[reflect-config-reminder] $FQN is not registered in reflect-config.json.
If this class is a @Tool/@McpTool adapter, @Document, or @JsonTypeInfo subtype,
add it before deploying. See .claude/skills/native-image-safety/SKILL.md.
EOF
fi

exit 0
