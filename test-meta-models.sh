#!/bin/bash
# Test Meta Model API access for Crop Scout. Key is read from the environment
# only (META_API_KEY) and is never written to a file, log, or repo.
# Usage: META_API_KEY='...' ./test-meta-models.sh [tray-photo.jpg]
set -euo pipefail

if [ -z "${META_API_KEY:-}" ]; then
  echo "ERROR: META_API_KEY is not set."
  echo "Run: export META_API_KEY='paste-your-key-here'"
  echo "Then re-run this script. Do not put the key in this file."
  exit 1
fi

MODEL="${META_MODEL:-muse-spark-1.3}"
BASE="${META_BASE_URL:-https://api.meta.ai/v1}"

echo "== 1. Muse Spark test (text only, model: $MODEL) =="
curl -sS -w "\nHTTP %{http_code}\n" "$BASE/chat/completions" \
  -H "Authorization: Bearer $META_API_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"model\":\"$MODEL\",\"messages\":[{\"role\":\"user\",\"content\":\"Reply with exactly: META-OK\"}],\"max_tokens\":200}" \
  | head -c 1000
echo
echo
echo "== 2. SAM 3.1 — see test-sam.sh =="
echo "SAM has its own probe script (request shape verified against the"
echo "dev.meta.ai SAM docs; a live call still needs your key + photo):"
echo "  META_API_KEY='...' ./test-sam.sh [concept] [tray-photo.jpg]"
echo "Photo argument was: ${1:-<none>}"
echo "Done. Unset the key when finished: unset META_API_KEY"
