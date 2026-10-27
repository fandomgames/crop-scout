# Crop Scout (SproutVision — Meta AI Hackathon, Indie track, "Build for Your World")

## Built before the hackathon (before Oct 26, 2026)
- SproutVision v2 base: HUD, Gemini scan, tray cards, history, delta, scorecard
- SAM segmentation core (Oct 25, 2026): sam-3.1 + muse-spark via Meta Model API

## Built during the hackathon (Oct 27–Nov 4, 2026)
- (to be added as built: golden validation, verdict trace, confirmation gate, cost controls)


# SproutVision — Quest 3 Edition (Grow HUD)

A microgreens grow assistant for **Meta Quest 3**, reworked HUD-first:
stand at the rack, see a compact grow HUD floating in passthrough, scan
trays with the headset's cameras, and record it all for content — the HUD
stays in frame in Quest screen recordings.

This is the native-Android port of the Android XR build in
`../sprout-vision/`. The data layer is shared verbatim (Room tray/stage
storage, DataStore settings, Gemini + demo analyzers, reminder scheduler,
ViewModel); only the XR shell changed (Jetpack XR Subspace → Meta Spatial
SDK) and the camera path (CameraX → Camera2 on the passthrough cameras).

## The two panels

- **Grow HUD** (0.70 × 0.45 m, head-anchored upper-right): the recording
  overlay. It follows your view, so it never leaves the recording frame;
  pinch-drag it to move it, or drag a corner handle to resize it.
  Current tray with Day N, stage, health and harvest countdown; ◀ ▶ tray
  switcher for walking down the rack; big **Scan tray** and **Log stage**
  buttons (hand-pinch / controller-ray friendly, ≥ 48 dp); scan results
  appear here too, so you never leave the recording view.
- **Manage panel** (1.05 × 0.70 m, center-left, hidden by default): the full
  tray dashboard — tray list, tray details + notes, add tray, settings.
  Opened from the HUD's **Manage** button; Back hides it again.

The Manage panel spawns in front of you at launch (LOCAL_FLOOR space), so
**launch the app while facing the grow rack**. The HUD is head-anchored
and follows your view wherever you look.

## Recording the plants with the HUD (for today)

1. Sideload + launch **SproutVision Quest** (below). Face the rack at launch.
2. Pick the tray with ◀ ▶, or open Manage to add your real trays.
3. Start the Quest system recorder: press the **Meta button** → quick
   settings → **Camera** → **Record video** (or hold Meta + trigger, OS
   version dependent). The recording captures passthrough *and* the HUD.
4. Walk the rack: switch trays, hit **Scan tray**, apply/log stages — it all
   lands in the video with the Day N / health readouts visible.
5. Stop recording from the same camera menu; the clip is in Files → Videos.

Tips: the HUD is parked upper-right so it doesn't cover the trays; keep
your head movements slow for watchable footage. Demo mode analyzes a sample
photo (scripted results) — turn it off in Manage → Settings and add a
Gemini API key for real analysis of your captures.

## Tray scanning

Capture uses the Quest 3 passthrough cameras via the Camera2 API
(`ui/QuestScanScreen.kt`). Grant camera permission when prompted. The photo
goes through the demo analyzer (scripted) or the Gemini Developer API
(needs your API key in Settings) for growth-stage detection; Apply stage
writes the detection into the tray's history.

## Requirements

- **Quest 3 / 3S** with **developer mode enabled**, Horizon OS v74+ (tray
  scanning needs the passthrough camera API).
- JDK 17. Android Studio Narwhal (2025.1.1)+ also works, but the project
  builds headless — see below.

## Build & sideload

Headless (no Android Studio needed):

```
export JAVA_HOME=/path/to/jdk17
export ANDROID_SDK_ROOT=/path/to/android-sdk   # needs platforms;android-35
export PATH=$ANDROID_SDK_ROOT/platform-tools:$PATH
gradle assembleDebug   # Gradle 8.10+; AGP is pinned at 8.7.3
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or in Android Studio: open the `sprout-vision-quest` folder, let Gradle
sync, connect the Quest 3 via USB (accept the RSA prompt in the headset),
and Run — or build + sideload manually with the commands above.

Launch **SproutVision Quest** from the app library (Unknown Sources if
sideloaded via adb). Grant camera permission when prompted.

## Input

Controllers and hands both work: pick up the controllers for
controller rays, or set them down and point with your hand ray —
pinch to click. First launch asks for hand-tracking permission in
the headset; it also requires Hand Tracking enabled under
Settings → Movement Tracking.

## First-sync checklist (things that move fast)

- **Spatial SDK imports**: if Gradle can't resolve a `com.meta.spatial.*`
  import in `QuestSproutActivity.kt`, check the exact package in the official
  samples: https://github.com/meta-quest/Meta-Spatial-SDK-Samples
- **Panel placement**: the Manage panel spawns relative to where you face
  at launch. If it appears behind you, relaunch while facing the rack (or
  negate the Z value in `onSceneReady()`). The HUD follows your head —
  tune `HUD_HEAD_OFFSET` in `QuestSproutActivity.kt` to move it.
- **Camera id**: `QuestScanScreen` logs `cameraIdList` and tries every id in
  order until one configures — Quest 3 exposes its left/right passthrough
  cameras here and some entries can be dead. Frames may be
  fisheye/distorted; fine for plant analysis, not for fine text.
- **Spatial SDK version** is pinned at `0.10.1` in `app/build.gradle.kts`.

## Known limitations

- Camera permission on Quest may need `horizonos.permission.HEADSET_CAMERA`
  instead of (or as well as) `android.permission.CAMERA` — both are declared
  in the manifest; if the runtime prompt never appears, that's the knob.
- No skybox is created on purpose: with passthrough on, the real room shows
  through behind the panels.
- Meta's current policy historically restricted camera-API apps from store
  publication — fine for sideloading, check VRCs before any store release.
- Daily reminders are best-effort on Quest (background work is throttled);
  you'll mostly open the app at the rack anyway.

## Project layout

```
app/src/main/java/com/farrah/sproutvision/
├── QuestSproutActivity.kt # AppSystemActivity: passthrough + HUD/manage panels
├── SproutVisionApp.kt     # Application class
├── data/                  # Room: trays, stages, repository (shared)
├── analyze/               # PlantAnalyzer, DemoAnalyzer, GeminiAnalyzer (shared)
├── reminders/             # WorkManager daily check (shared, best-effort on Quest)
├── settings/              # DataStore: API key, demo mode (shared)
├── util/                  # DateUtils (shared)
└── ui/
    ├── HudNav.kt / HudScreen.kt / HudResultScreen.kt  # grow-HUD panel
    ├── ManageNav.kt       # tray-management panel (dashboard/detail/add/settings)
    ├── DashboardScreen.kt / TrayDetailScreen.kt / … (shared)
    ├── SproutViewModel.kt # (shared) + HUD state (hudScreen, manageVisible)
    └── QuestScanScreen.kt  # Camera2 tray capture (Quest replacement for
                           #   the CameraX ScanScreen)
```
