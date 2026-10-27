#!/bin/bash
# Probe SAM 3.1 on the Meta Model API (Meta playground's flow, Oct 25).
# Two steps: upload the photo to /v1/files (purpose=user_data), then POST
# /v1/responses with model sam-3.1, one input_text concept, and an
# input_image item carrying the returned file ID. Streamed, lossless.
# Key is read from the environment only (META_API_KEY) and is never written
# to a file, log, or repo. The key travels in Authorization headers and
# is never echoed (no -v/--trace anywhere in this script).
# Usage: META_API_KEY='...' ./test-sam.sh [concept] [tray-photo.jpg]
#   concept defaults to "microgreens"; without a photo the script uploads
#   a 1x1 PNG (proves auth/endpoint/shape, but expect zero masks).
set -euo pipefail

if [ -z "${META_API_KEY:-}" ]; then
  echo "ERROR: META_API_KEY is not set."
  echo "Run: export META_API_KEY='paste-your-key-here'"
  echo "Then re-run this script. Do not put the key in this file."
  exit 1
fi
if ! command -v python3 >/dev/null 2>&1; then
  echo "ERROR: python3 is required (builds the request body)."
  exit 1
fi

MODEL="${META_SAM_MODEL:-sam-3.1}"
BASE="${META_BASE_URL:-https://api.meta.ai/v1}"
CONCEPT="${1:-microgreens}"
PHOTO="${2:-}"
TMPDIR="$(mktemp -d)"
trap 'rm -rf "$TMPDIR"' EXIT

if [ -n "$PHOTO" ]; then
  if [ ! -f "$PHOTO" ]; then
    echo "ERROR: photo not found: $PHOTO"
    exit 1
  fi
  case "$PHOTO" in
    *.jpg | *.JPG | *.jpeg | *.JPEG | *.png | *.PNG) ;;
    *)
      echo "ERROR: photo must be .jpg/.jpeg/.png: $PHOTO"
      exit 1
      ;;
  esac
  UPLOAD="$PHOTO"
  echo "Photo: $PHOTO"
else
  # 1x1 green PNG fallback (proves the call path without a tray photo).
  echo "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGNgaGAAAAEEAIFw9selAAAAAElFTkSuQmCC" \
    | base64 -d > "$TMPDIR/tiny.png"
  UPLOAD="$TMPDIR/tiny.png"
  echo "No photo given: uploading a 1x1 PNG (expect zero masks; this still proves auth/endpoint/shape)."
fi

echo "Concept: $CONCEPT"
echo "Model: $MODEL"
echo "== step 1: upload to /v1/files =="
UPLOAD_RESPONSE="$(curl --fail-with-body -sS "$BASE/files" \
  -H "Authorization: Bearer $META_API_KEY" \
  -F "purpose=user_data" \
  -F "file=@$UPLOAD;type=image/jpeg")"
echo "Upload response: $(echo "$UPLOAD_RESPONSE" | head -c 200)"

echo "== step 2: stream /v1/responses (Accept: text/event-stream, mask_encoding lossless) =="
REQUEST_BODY="$(python3 - "$UPLOAD_RESPONSE" "$CONCEPT" <<'PYTHON'
import json
import sys

upload = json.loads(sys.argv[1])
file_id = upload.get("id")
if not isinstance(file_id, str) or not file_id:
    raise ValueError("Upload response did not contain a valid file id.")
print(json.dumps({
    "model": "sam-3.1",
    "input": [{
        "type": "message",
        "role": "user",
        "content": [
            {"type": "input_text", "text": sys.argv[2]},
            {"type": "input_image", "file_id": file_id},
        ],
    }],
    "stream": True,
    "metadata": {"mask_encoding": "lossless"},
}))
PYTHON
)"
TMP="$TMPDIR/stream.txt"
HTTP="$(curl -sS -N --max-time 120 -o "$TMP" -w "%{http_code}" "$BASE/responses" \
  -H "Authorization: Bearer $META_API_KEY" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d "$REQUEST_BODY")"
echo "HTTP $HTTP"
echo "Bytes: $(wc -c < "$TMP" | tr -d ' ')"
echo "== event census (SSE event: lines) =="
grep -E '^event: ' "$TMP" | sort | uniq -c | sort -rn || echo "(no event: lines — not an SSE stream?)"
echo "== data payloads with an embedded type field: $(grep -c 'data:.*"type"' "$TMP" || true) =="
echo "== first 1500 bytes of stream =="
head -c 1500 "$TMP"
echo
echo
for WANT in 'output_text.done' 'content_part.done' 'response.completed' 'response.incomplete' 'response.failed' 'refusal' '"error"'; do
  HIT="$(grep -m1 -o ".\{0,80\}$WANT.\{0,500\}" "$TMP" || true)"
  if [ -n "$HIT" ]; then
    echo "== first match for $WANT (truncated) =="
    echo "$HIT"
    echo
  fi
done
echo "== delta events: $(grep -c 'output_text.delta' "$TMP" || true) =="
echo "Done. Unset the key when finished: unset META_API_KEY"
