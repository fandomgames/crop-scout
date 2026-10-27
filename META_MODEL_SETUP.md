# Meta Model API setup — Crop Scout (hackathon copy only)

This copy is the hackathon working copy. The v2 source is paused and frozen in
`../sprout-vision-quest-v2-frozen/` — do not edit that copy for hackathon work.

BASE (2026-10-22, rebased): this copy is built from Farrah's REAL Mac v2
source (zip received 2026-10-22), NOT the stale Oct 12-13 workspace copy
that the first hackathon build used. That stale base lacked Scorecard,
scan history + CSV export, cinematic scan, 9:16 viewfinder, anchored tray
cards, GrowIntent, voice readout, and the grabbable head-anchored HUD.
It also lacked the manifest declarations below, which is why the first
hackathon build said "grab controllers" and showed a black void instead
of passthrough. This rebased copy keeps all real v2 features.

Hand tracking + passthrough — ALREADY PRESENT in the real v2 base, do
NOT re-add them: the real AndroidManifest declares
`com.oculus.permission.HAND_TRACKING` /
`horizonos.permission.HAND_TRACKING` and
`oculus.software.handtracking` (required=false), plus
`com.oculus.feature.PASSTHROUGH` (the flag without which
`scene.enablePassthrough()` silently leaves a black void). The real
QuestSproutActivity also requests hand-tracking permission at runtime
and makes the HUD/Manage panels Grabbable (pinch-drag to reposition,
panel-lock toggle in the HUD). If hand tracking or passthrough still
fails in-headset on this rebased copy, it is a system setting / launch
state problem, not a missing manifest flag — check Quest Settings
hand tracking and the Quick Settings passthrough toggle first.

## Step 1 — Get a key (only Farrah can do this part)

1. Sign in / create an account at https://dev.meta.ai
2. Open the Meta Model API dashboard.
3. Generate a key. Keys are typically shown once — save it securely.
4. Check what credits are actually showing in the dashboard. Do NOT assume
   the $150 figure from earlier planning notes — the Cerebral Valley event
   page lists partner credits for open-weights models (Baseten $100, Modal
   $100, Fireworks $25), not a Meta Model API credit. Record what you see.
5. Never put the key in chat, in a repo, in a screenshot, or in a demo video.
   In the app it goes in Manage → Settings → "Meta Model API key (hackathon)",
   which stores it per-user in DataStore, like the Gemini key.

## Step 2 — Verify the model IDs at kickoff (Oct 26)

Third-party guides list:
- Muse Spark: `muse-spark-1.3`
- SAM: `sam-3.1`
- Base URL: `https://api.meta.ai/v1`

Verify all three against Meta's current docs before trusting a test result.
If any differ, update `MetaAnalyzer.DEFAULT_MODEL` / `BASE_URL` first.

## Step 3 — Run the test script (no key stored)

The script reads the key from the environment only, for one shell session:

```bash
export META_API_KEY='paste-your-key-here'
./test-meta-models.sh path/to/a/tray-photo.jpg
```

It makes:
1. One Muse Spark chat-completions call (small text prompt, no image, so a
   bad key / bad model ID fails fast and cheaply).
2. One SAM note — SAM's response shape is NOT yet verified, so the script
   does not pretend to parse a mask. It prints what to check in Meta's SAM
   docs and stops there. Implement `SamClient` from those docs, then decode
   the mask into a 0/1 grid and feed it to `SamSegmenter.fromMask()`.

If either call fails on kickoff day, that is a Day 1 blocker — ask in the
Discord office hours, do not burn two days on it alone.

## Step 4 — Turn Meta on in the app

1. Build and sideload THIS copy only: `gradle assembleDebug`, then
   `adb install app/build/outputs/apk/debug/app-debug.apk`
   (per sprout-vision-quest AGENTS.md: a successful build is never
   announced as a working app — verify in-headset).
2. Launch facing the grow rack, open Manage → Settings.
3. Paste the Meta key, save it, turn ON "Use Meta AI (Crop Scout)".
4. Scan a real tray. Compare the verdict to your by-eye verdict, and to
   the Gemini verdict on the same tray.
5. Keep Gemini as the fallback: turning the Meta toggle OFF returns to
   Gemini/Demo. Do not delete GeminiAnalyzer.

## What is already wired in this copy

- `analyze/MetaAnalyzer.kt` — Muse Spark, Chat Completions shape, structured
  JSON verdict, with optional SAM summary and grow-plan context.
- `analyze/SamSegmenter.kt` — pure mask math: coverage %, largest gap,
  unevenness, plus a summary string for Spark. Testable offline.
- `analyze/Segmenter.kt` + `MetaSamSegmenter.kt` + `DemoSegmenter.kt` —
  SAM 3.1 measurement (tray + plant requests, union coverage) with an
  offline green-pixel fallback. The ViewModel runs the segmenter before
  Spark and falls back to Spark-only with a "SAM measurement unavailable"
  note — it never invents a SAM number. Gated by Settings → "Use SAM
  measurement" (kill-switch: off = zero SAM network calls).
- `settings/SettingsStore.kt` + `ui/SettingsScreen.kt` + `ui/SproutViewModel.kt`
  — Meta key storage, "Use Meta AI" toggle, and analyzer selection:
  Demo > Meta (if on + key present) > Gemini (if key present) > Demo.
  Adapted for the real v2 base: PlantAnalyzer now takes an onEvent
  progress callback (passed through to Spark), AnalysisResult now
  requires trayVisible/trayBox and carries confidence (Meta returns
  trayVisible=true, trayBox=null, confidence from Spark or 0; the
  ViewModel overwrites trayBox with the SAM-measured box when
  segmentation ran), and
  the ViewModel derives the Meta growPlan from the scanned tray's
  GrowIntent (GROW_OUT -> grown-out-to-maturity wording, matching
  GeminiAnalyzer). A blank Gemini key no longer forces demo when a
  Meta key is present.

## What is NOT done yet

- [ ] Meta key created and credits confirmed in the dashboard
- [ ] Model IDs / SAM response shape verified against Meta's current docs
- [x] SAM API client implemented (/v1/files upload + sam-3.1 file-ID
      requests, one_bit + lossless mask decoding ported from
      meta-sam-parser 0.1.0 and verified against Meta's own decoder)
- [ ] One live SAM call + one live Spark call on a real tray photo
      (`./test-sam.sh "microgreens" tray.jpg`, then the in-headset scan)
- [ ] In-headset verification of a Meta verdict
- [x] Grow-plan field wired from a tray: ViewModel derives it from
      tray.growIntent (GROW_OUT) and passes it to MetaAnalyzer.
- [ ] Hand-tracking verification in-headset on the rebased copy
      (manifest + runtime permission are present in real v2 — verify,
      do not re-add blindly)
- [ ] Passthrough verification in-headset on the rebased copy
      (com.oculus.feature.PASSTHROUGH is present in real v2 — verify,
      do not re-add blindly)
