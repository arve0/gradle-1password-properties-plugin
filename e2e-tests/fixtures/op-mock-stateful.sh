#!/usr/bin/env bash
set -euo pipefail
counter_file="${INVOCATION_COUNT_FILE}"
if [ -f "$counter_file" ]; then
  count=$(cat "$counter_file")
else
  count=0
fi
count=$((count + 1))
printf '%s' "$count" > "$counter_file"
cat "${SECRET_FILE}"
