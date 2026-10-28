package com.farrah.sproutvision

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import com.farrah.sproutvision.data.HerbariumEntity
import com.farrah.sproutvision.data.TrayEntity
import com.farrah.sproutvision.data.idLabelVisible
import com.farrah.sproutvision.settings.AppSettings
import androidx.compose.ui.unit.dp
import com.farrah.sproutvision.ui.HUD_COMPACT_DESIGN_HEIGHT_DP
import com.farrah.sproutvision.ui.HUD_COMPACT_DESIGN_WIDTH_DP
import com.farrah.sproutvision.ui.HUD_DESIGN_HEIGHT_DP
import com.farrah.sproutvision.ui.HUD_DESIGN_WIDTH_DP
import com.farrah.sproutvision.ui.HudNav
import com.farrah.sproutvision.ui.ScaledHudPanel
import com.farrah.sproutvision.ui.HudScreen
import com.farrah.sproutvision.ui.ManageNav
import com.farrah.sproutvision.ui.SproutViewModel
import com.farrah.sproutvision.ui.IdLabelRoute
import com.farrah.sproutvision.ui.TrayCardRoute
import com.farrah.sproutvision.ui.VerticalViewfinderOverlay
import com.farrah.sproutvision.ui.verticalViewfinderVisible
import com.farrah.sproutvision.ui.theme.SproutVisionTheme
import com.farrah.sproutvision.ui.trayCardVisible
import com.farrah.sproutvision.util.ScanChime
import com.farrah.sproutvision.util.VoiceReadout
import com.farrah.sproutvision.util.WakeLog
import com.meta.spatial.compose.ComposeFeature
import com.meta.spatial.compose.ComposeViewPanelRegistration
import com.meta.spatial.isdk.IsdkPanelResize
import com.meta.spatial.isdk.ResizeMode
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.DpDisplayOptions
import com.meta.spatial.toolkit.Grabbable
import com.meta.spatial.toolkit.GrabbableType
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.runtime.PanelShapeLayerBlendType
import com.meta.spatial.toolkit.PanelRenderMode
import com.meta.spatial.toolkit.QuadShapeOptions
import com.meta.spatial.toolkit.UIPanelRenderOptions
import com.meta.spatial.toolkit.UIPanelSettings
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.Vector3
import com.meta.spatial.runtime.ReferenceSpace
import com.meta.spatial.runtime.SessionState
import com.meta.spatial.toolkit.Panel
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible
import com.meta.spatial.vr.VRFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.Volatile

/**
 * SproutVision on Quest 3 — grow-HUD edition.
 *
 * Two spatial panels float in passthrough over the grow rack:
 * - **HUD panel** (compact, head-anchored upper-right): the recording HUD.
 *   It follows the view so it never leaves the recording frame; pinch-drag
 *   it to reposition, and drag a corner handle to resize; it keeps
 *   following from its new spot and size. Current tray,
 *   Day N, stage, health, tray switcher, one-tap tray scan and stage logging.
 *   Small enough to stay out of the shot while the Quest system recorder runs.
 * - **Manage panel** (large, hidden by default): full tray dashboard — tray
 *   list, details, add tray, settings. Opened from the HUD's "Manage" button.
 * - **Vertical HUD** (compact head-tracked, hidden by default): the same
 *   HUD content in a dense small-panel layout, floating upper-right inside
 *   the viewfinder frame for 9:16 capture. Swapped in by the 9:16 toggle;
 *   never visible alongside the main HUD.
 * - **Viewfinder** (transparent guide-only, hidden by default): the 9:16
 *   frame guide for shooting shorts — border, corner brackets, "9:16"
 *   label. Not interactive; hidden unless vertical mode is ON with the
 *   HUD home/result showing and Manage closed.
 * - **Tripwire panel** (invisible, clean-mode only): a large transparent
 *   head-locked quad — tapping anywhere in view restores the panels.
 * - **Tray cards** (one runtime-registered panel per tray): small cards
 *   pinned at each tray's rack position; placed via Manage → Place cards.
 *
 * Ember & Ash lessons applied: passthrough on with no skybox (the real room
 * shows through), HUD parked in the upper-right corner out of the action,
 * and hand-pinch/controller-ray sized touch targets (≥ 48dp).
 *
 * Recording tip: launch this app while facing the grow rack — panels spawn in
 * front of you in LOCAL_FLOOR space. Then start the Quest system recorder;
 * it captures passthrough + both panels.
 *
 * NOTE (first-sync checklist): if Gradle can't resolve a com.meta.spatial
 * import below, check the exact package in the official samples:
 * https://github.com/meta-quest/Meta-Spatial-SDK-Samples
 */
class QuestSproutActivity : AppSystemActivity() {

    private val vm: SproutViewModel by lazy { SproutViewModel(application) }

    private var hudEntity: Entity? = null
    private var hudVerticalEntity: Entity? = null
    private var tripwireEntity: Entity? = null
    private var viewfinderEntity: Entity? = null
    private var manageEntity: Entity? = null
    private var hudWasGrabbed = false
    /** True once onSceneReady has built the panel entities (cold start). */
    private var sceneEntitiesCreated = false
    /** Last onSceneTick timestamp: a post-wake gap means the loop died. */
    private var lastTickMs = 0L
    /** Session watchdog: fires if no live session state arrives in time. */
    private var sessionWatchdog: Job? = null
    private var sessionAliveSinceResume = false
    private var hadLiveSession = false
    private val panelJobs = mutableListOf<Job>()
    /**
     * Last-known visibility of the 9:16 pair, cached by the visibility
     * collector so the scene tick only re-pins panels that are actually
     * showing (a stale read lags one frame at most, then self-heals).
     */
    @Volatile private var compactHudTracked = false
    @Volatile private var viewfinderTracked = false
    @Volatile private var hudTracked = true
    @Volatile private var manageTracked = false

    /**
     * Persistent per-HUD front-ordering state (see HudDepthOrder.kt):
     * the smoothed depth nudge plus the locked-mode anchor it is
     * applied around. The anchor never absorbs the nudge, so
     * locking/unlocking and grab-release never jump or stick.
     */
    private class HudFrontState {
        var depthM = 0f
        var anchor: Pose? = null
        var wasLocked = false

        fun reset() {
            depthM = 0f
            anchor = null
            wasLocked = false
        }
    }
    private val hudFrontState = HudFrontState()
    private val compactFrontState = HudFrontState()
    private val manageFrontState = HudFrontState()
    private var manageWasGrabbed = false

    /** One runtime-registered floating card per tray, keyed by tray id. */
    private data class TrayCard(
        val trayId: Long,
        val entity: Entity,
        var anchored: Boolean = false,
        var grabEnabled: Boolean = false,
        var wasGrabbed: Boolean = false,
        var ghostSlot: Int = 0
    )
    private val trayCards = ConcurrentHashMap<Long, TrayCard>()
    /** One runtime-registered floating ID label per herbarium entry, keyed by entry id. */
    private data class IdLabel(
        val entryId: Long,
        val entity: Entity,
        var anchored: Boolean = false,
        var grabEnabled: Boolean = false,
        var wasGrabbed: Boolean = false
    )
    private val idLabels = ConcurrentHashMap<Long, IdLabel>()
    // Runtime panel ids: 1e9 (0x3B9ACA00) can never collide with aapt ids,
    // which always carry the 0x7F package prefix.
    private var nextCardPanelId = 1_000_000_000
    /** Sticky ghost fan slots by tray id (see assignGhostSlots). */
    private val ghostSlots = mutableMapOf<Long, Int>()

    // Back + activity-result plumbing that ComponentActivity normally provides.
    private val backDispatcher = OnBackPressedDispatcher()
    private val backDispatcherOwner = object : OnBackPressedDispatcherOwner {
        override val lifecycle: Lifecycle = LifecycleRegistry(this)
        override val onBackPressedDispatcher: OnBackPressedDispatcher get() = backDispatcher
    }
    private val activityResultRegistry = object : ActivityResultRegistry() {
        @Suppress("DEPRECATION")
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {
            // Permission contracts have no launchable Intent — route them through
            // requestPermissions like ComponentActivity does (their results come
            // back via onRequestPermissionsResult and are forwarded below).
            val activity = this@QuestSproutActivity
            if (contract is ActivityResultContracts.RequestMultiplePermissions) {
                @Suppress("UNCHECKED_CAST")
                activity.requestPermissions(input as Array<String>, requestCode)
                return
            }
            if (contract is ActivityResultContracts.RequestPermission) {
                activity.requestPermissions(arrayOf(input as String), requestCode)
                return
            }
            activity.startActivityForResult(
                contract.createIntent(this@QuestSproutActivity, input),
                requestCode,
                options?.toBundle()
            )
        }
    }
    private val activityResultRegistryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry
            get() = this@QuestSproutActivity.activityResultRegistry
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WakeLog.log(this, "onCreate")
        // Quest system keyboard: without this flag, tapping a text field in
        // a spatial panel focuses it but the keyboard never appears — the
        // fullscreen VR window blocks the IME. Official Meta workaround for
        // AppSystemActivity panels ("[Spatial SDK] Virtual keyboard is not
        // shown", Meta employee answer); SDK 0.10.1 sets no window flags
        // itself, so this is still required.
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        moveOwnerLifecycle(Lifecycle.Event.ON_CREATE)
        // Hand ray + pinch-to-click on both panels. Controllers keep
        // working regardless; if the user denies this, hands just won't track.
        val handsGranted = HAND_TRACKING_PERMISSIONS.any {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!handsGranted) {
            requestPermissions(HAND_TRACKING_PERMISSIONS, HAND_TRACKING_REQUEST_CODE)
        }
        // Fresh installs start with an empty tray table — no auto-seed.
        // Sample data is opt-in via Settings → Load demo trays.
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == HAND_TRACKING_REQUEST_CODE) {
            val results = permissions.zip(
                grantResults.map { it == PackageManager.PERMISSION_GRANTED }
            ).toMap()
            Log.i(TAG, "Hand tracking permission results=$results")
        } else {
            val data = Intent()
                .putExtra(
                    ActivityResultContracts.RequestMultiplePermissions.EXTRA_PERMISSIONS,
                    arrayOf(*permissions)
                )
                .putExtra(
                    ActivityResultContracts.RequestMultiplePermissions.EXTRA_PERMISSION_GRANT_RESULTS,
                    grantResults
                )
            activityResultRegistry.dispatchResult(requestCode, Activity.RESULT_OK, data)
        }
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onBackPressed() {
        // Route to Compose BackHandlers when any is enabled, else keep the
        // system default (an empty dispatcher would swallow the press).
        if (backDispatcher.hasEnabledCallbacks()) backDispatcher.onBackPressed()
        else super.onBackPressed()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        activityResultRegistry.dispatchResult(requestCode, resultCode, data)
    }

    override fun onStart() {
        super.onStart()
        WakeLog.log(this, "onStart")
        moveOwnerLifecycle(Lifecycle.Event.ON_START)
    }

    override fun onResume() {
        // VrActivity runs onSceneReady only on the FIRST resume
        // (hasReadied guard). Every later resume is a wake from sleep: the
        // session restarted, the panels' native objects died with it, and
        // nothing rebuilds them — panels stay gone until relaunch.
        // Recreate the panel entities; visibility and poses restore from
        // the surviving vm state and snapshots (see recreatePanelEntities).
        val waking = sceneEntitiesCreated
        WakeLog.log(this, "onResume waking=$waking")
        super.onResume()
        moveOwnerLifecycle(Lifecycle.Event.ON_RESUME)
        sessionAliveSinceResume = false
        armSessionWatchdog(WATCHDOG_MS)
        if (waking) recreatePanelEntities()
    }

    override fun onPause() {
        WakeLog.log(this, "onPause")
        saveUiState()
        moveOwnerLifecycle(Lifecycle.Event.ON_PAUSE)
        super.onPause()
    }

    override fun onStop() {
        WakeLog.log(this, "onStop")
        moveOwnerLifecycle(Lifecycle.Event.ON_STOP)
        super.onStop()
    }

    override fun onDestroy() {
        WakeLog.log(this, "onDestroy")
        sessionWatchdog?.cancel()
        sessionWatchdog = null
        panelJobs.forEach { it.cancel() }
        panelJobs.clear()
        moveOwnerLifecycle(Lifecycle.Event.ON_DESTROY)
        VoiceReadout.shutdown()
        ScanChime.shutdown()
        super.onDestroy()
    }

    private fun moveOwnerLifecycle(event: Lifecycle.Event) {
        (backDispatcherOwner.lifecycle as LifecycleRegistry).handleLifecycleEvent(event)
    }

    // Wake-path diagnostics only: super preserves behavior, the log line
    // records which callbacks actually fire across sleep/wake.
    override fun onSessionStateChanged(state: SessionState) {
        super.onSessionStateChanged(state)
        WakeLog.log(this, "session=${state.name}")
        if (state == SessionState.FOCUSED || state == SessionState.VISIBLE ||
            state == SessionState.SYNCHRONIZED || state == SessionState.READY
        ) {
            // Live session: cancel any pending recovery.
            sessionAliveSinceResume = true
            hadLiveSession = true
            sessionWatchdog?.cancel()
            sessionWatchdog = null
            WakeLog.log(this, "watchdog cancelled ${state.name}")
        } else if (state == SessionState.STOPPING || state == SessionState.EXITING ||
            state == SessionState.LOSS_PENDING ||
            (state == SessionState.IDLE && hadLiveSession)
        ) {
            // Unexpected session death (IDLE only counts after a live
            // session — cold start passes through IDLE legitimately).
            // Recheck soon instead of waiting out the full watchdog.
            WakeLog.log(this, "session died ${state.name}, fast watchdog")
            armSessionWatchdog(FAST_WATCHDOG_MS)
        }
    }

    override fun onVRReady() {
        super.onVRReady()
        WakeLog.log(this, "vrReady")
    }

    override fun onVRPause() {
        super.onVRPause()
        WakeLog.log(this, "vrPause")
    }

    override fun onHMDMounted() {
        super.onHMDMounted()
        WakeLog.log(this, "hmdMounted")
    }

    override fun onHMDUnmounted() {
        super.onHMDUnmounted()
        WakeLog.log(this, "hmdUnmounted")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        WakeLog.log(this, "windowFocus=$hasFocus")
    }

    override fun registerFeatures(): List<SpatialFeature> =
        listOf(VRFeature(this), ComposeFeature())

    // 9:16 vertical-capture numbers — tune in-headset. Final framing is
    // verified with a test recording, then these get corrected. Both the
    // compact HUD and the viewfinder track the head every tick, so the HUD
    // stays glued inside the frame however the user turns.
    // - viewfinderSize: the shorts crop guide, true 9:16 (0.9 × 1.6 m).
    // - viewfinderDepthM: head-space depth of the guide. Spec 1.5 m + 2 cm
    //   so the compact HUD (1.50 m) stays in front: nearer panels win ray
    //   input, and coplanar quads would flicker.
    // - compactHudSize / compactHudPos: the small HUD's meter size and
    //   LOCAL_FLOOR spawn (upper-right inside the viewfinder at launch
    //   orientation). compactHudOffset is its head-tracked equivalent.
    private val viewfinderSize = Pair(0.9f, 1.6f) // width × height, meters
    private val viewfinderDepthM = 1.52f // meters in head space
    private val viewfinderOffset = Pose(Vector3(0f, 0f, viewfinderDepthM))
    private val viewfinderSpawn = Vector3(0f, 1.62f, viewfinderDepthM) // LOCAL_FLOOR; re-pinned next tick
    private val hudSpawn = Pose(Vector3(0.30f, 1.74f, 0.85f))
    private val manageSpawn = Pose(Vector3(-0.30f, 1.35f, 1.55f))
    private val tripwireSpawn = Pose(Vector3(0f, 1.6f, 1.0f))
    private val compactHudSize = Pair(0.45f, 0.32f) // width × height, meters
    private val compactHudPos = Vector3(0.20f, 2.02f, 1.50f) // LOCAL_FLOOR spawn
    private val compactHudOffset = Pose(Vector3(0.20f, 0.40f, 1.50f)) // ≈ compactHudPos at 1.62 m eye height

    override fun registerPanels(): List<PanelRegistration> = listOf(
        // Compact grow HUD — the recording overlay.
        ComposeViewPanelRegistration(
            registrationId = R.id.panel_hud,
            composeViewCreator = { _, context ->
                ComposeView(context).apply {
                    setContent {
                        val settings by vm.settings.collectAsState()
                        CompositionLocalProvider(
                            LocalOnBackPressedDispatcherOwner provides backDispatcherOwner,
                            LocalActivityResultRegistryOwner provides activityResultRegistryOwner
                        ) {
                            SproutVisionTheme(darkMode = settings.darkMode) {
                                ScaledHudPanel(HUD_DESIGN_WIDTH_DP.dp, HUD_DESIGN_HEIGHT_DP.dp) { HudNav(vm) }
                            }
                        }
                    }
                }
            },
            settingsCreator = {
                UIPanelSettings(
                    shape = QuadShapeOptions(width = 0.70f, height = 0.45f),
                    display = DpDisplayOptions(width = 780f, height = 500f),
                    // Compositor layer with real alpha blending: the HUD
                    // background is translucent, so MASKED (cutout) would
                    // render it solid or holey. Front-vs-cards order is
                    // enforced by the depth nudge in onSceneTick, not by
                    // the mode — overlapping layers order by depth.
                    rendering = UIPanelRenderOptions(
                        PanelRenderMode.Layer(
                            layerBlendType = PanelShapeLayerBlendType.ALPHA_BLEND,
                            enableLayerFeatheredEdge = true
                        )
                    )
                )
            }
        ),
        // Vertical-video HUD — same content in the dense compact layout
        // for 9:16 capture. Only one of the two HUD panels is ever visible.
        ComposeViewPanelRegistration(
            registrationId = R.id.panel_hud_vertical,
            composeViewCreator = { _, context ->
                ComposeView(context).apply {
                    setContent {
                        val settings by vm.settings.collectAsState()
                        CompositionLocalProvider(
                            LocalOnBackPressedDispatcherOwner provides backDispatcherOwner,
                            LocalActivityResultRegistryOwner provides activityResultRegistryOwner
                        ) {
                            SproutVisionTheme(darkMode = settings.darkMode) {
                                ScaledHudPanel(HUD_COMPACT_DESIGN_WIDTH_DP.dp, HUD_COMPACT_DESIGN_HEIGHT_DP.dp) {
                                    HudNav(vm, compact = true)
                                }
                            }
                        }
                    }
                }
            },
            settingsCreator = {
                UIPanelSettings(
                    shape = QuadShapeOptions(
                        width = compactHudSize.first,
                        height = compactHudSize.second
                    ),
                    display = DpDisplayOptions(width = 450f, height = 320f),
                    // Same compositor setup as the main HUD: real alpha
                    // blending for the translucent background (MASKED would
                    // cut it out), front-vs-cards via the tick depth nudge.
                    rendering = UIPanelRenderOptions(
                        PanelRenderMode.Layer(
                            layerBlendType = PanelShapeLayerBlendType.ALPHA_BLEND,
                            enableLayerFeatheredEdge = true
                        )
                    )
                )
            }
        ),
        // 9:16 viewfinder — transparent guide-only frame for shooting
        // shorts. No buttons, no clickables anywhere in its content: it
        // never takes input. Only visible while vertical mode is ON with
        // the HUD framing and Manage closed (see onSceneReady).
        ComposeViewPanelRegistration(
            registrationId = R.id.panel_viewfinder,
            composeViewCreator = { _, context ->
                ComposeView(context).apply {
                    // No theme wrapper: the overlay draws nothing but guide
                    // strokes, so passthrough stays perfectly clean, and the
                    // label carries its own scrim.
                    setContent { VerticalViewfinderOverlay() }
                }
            },
            settingsCreator = {
                UIPanelSettings(
                    shape = QuadShapeOptions(
                        width = viewfinderSize.first,
                        height = viewfinderSize.second
                    ),
                    display = DpDisplayOptions(width = 900f, height = 1600f)
                )
            }
        ),
        // Full tray management — shown on demand from the HUD.
        ComposeViewPanelRegistration(
            registrationId = R.id.panel_manage,
            composeViewCreator = { _, context ->
                ComposeView(context).apply {
                    setContent {
                        val settings by vm.settings.collectAsState()
                        CompositionLocalProvider(
                            LocalOnBackPressedDispatcherOwner provides backDispatcherOwner,
                            LocalActivityResultRegistryOwner provides activityResultRegistryOwner
                        ) {
                            SproutVisionTheme(darkMode = settings.darkMode) { ManageNav(vm) }
                        }
                    }
                }
            },
            settingsCreator = {
                UIPanelSettings(
                    shape = QuadShapeOptions(width = 1.05f, height = 0.70f),
                    display = DpDisplayOptions(width = 1050f, height = 700f)
                )
            }
        ),
        // Clean-mode tripwire — a large invisible head-locked quad. Any tap
        // on it (i.e. anywhere in view) restores the panels. Its content
        // draws nothing, so passthrough stays perfectly clean.
        ComposeViewPanelRegistration(
            registrationId = R.id.panel_tripwire,
            composeViewCreator = { _, context ->
                ComposeView(context).apply {
                    setContent {
                        CompositionLocalProvider(
                            LocalOnBackPressedDispatcherOwner provides backDispatcherOwner,
                            LocalActivityResultRegistryOwner provides activityResultRegistryOwner
                        ) {
                            BackHandler { vm.cleanMode = false }
                            Box(Modifier.fillMaxSize().clickable { vm.cleanMode = false })
                        }
                    }
                }
            },
            settingsCreator = {
                UIPanelSettings(
                    shape = QuadShapeOptions(width = 1.40f, height = 0.90f),
                    display = DpDisplayOptions(width = 700f, height = 450f)
                )
            }
        )
    )

    override fun onSceneReady() {
        super.onSceneReady()
        Log.i(TAG, "onSceneReady")
        WakeLog.log(this, "onSceneReady")
        // Fresh scene = fresh native handles: drop any tray-card entities
        // from a previous scene. Same-instance re-ready would otherwise
        // leave dangling handles behind (their scene is gone) and the next
        // card sync would crash touching them. No destroy() — dead handles
        // must not be touched; the wrappers are simply dropped for GC. On
        // a normal cold start the map is already empty, so this is a no-op.
        if (trayCards.isNotEmpty()) {
            Log.i(TAG, "onSceneReady: dropping ${trayCards.size} stale tray cards")
            trayCards.clear()
            nextCardPanelId = 1_000_000_000
        }
        if (idLabels.isNotEmpty()) {
            Log.i(TAG, "onSceneReady: dropping ${idLabels.size} stale ID labels")
            idLabels.clear()
        }
        scene.setReferenceSpace(ReferenceSpace.LOCAL_FLOOR)

        // No skybox entity is created — with passthrough enabled the real
        // grow room shows through behind both panels.
        scene.enablePassthrough(true)
        // Auto-recreate restore (no-op on ordinary starts).
        restoreUiState()

        // HUD: head-anchored upper-right (re-pinned in onSceneTick), so it
        // follows the view and never leaves the recording frame. Grabbable —
        // pinch-drag to reposition, it keeps following from the new spot.
        hudEntity = createHudEntity(hudSpawn, true)
        // Manage panel: center-left, hidden until the HUD's Manage button.
        manageEntity = createManageEntity(manageSpawn, false)
        // Clean-mode tripwire (see registerPanels): hidden until clean mode.
        tripwireEntity = createTripwireEntity(tripwireSpawn, false)
        // Vertical-video HUD: spawns at the compact preset, hidden until
        // the toggle, then head-tracked (re-pinned in onSceneTick) so it
        // stays inside the viewfinder frame. The main HUD entity is never
        // moved or resized by the mode, so toggling OFF restores it exactly.
        hudVerticalEntity = createCompactHudEntity(Pose(compactHudPos), false)
        // 9:16 viewfinder: transparent guide-only frame, hidden until the
        // toggle, then head-tracked like the compact HUD.
        viewfinderEntity = createViewfinderEntity(Pose(viewfinderSpawn), false)
        panelJobs += CoroutineScope(Dispatchers.Main).launch {
            // Vertical video swaps the HUD preset; clean mode hides
            // whichever HUD is active. Each emission is guarded so one bad
            // update logs and continues instead of killing the loop and
            // freezing the panels in their last state.
            combine(
                vm.settings,
                snapshotFlow { vm.cleanMode },
                snapshotFlow { vm.manageVisible },
                snapshotFlow { vm.hudScreen },
                snapshotFlow { vm.hideViewfinder }
            ) { settings, clean, manage, screen, hideFinder ->
                currentPanelVis(settings, clean, manage, screen, hideFinder)
            }.collect { vis ->
                applyPanelVis(vis)
            }
        }
        panelJobs += CoroutineScope(Dispatchers.Main).launch {
            combine(
                vm.trays,
                snapshotFlow { vm.placementMode },
                snapshotFlow { vm.cleanMode }
            ) { trays, placing, clean -> Triple(trays, placing, clean) }
                .collect { (trays, placing, clean) ->
                    syncTrayCards(trays, placing, clean)
                }
        }
        panelJobs += CoroutineScope(Dispatchers.Main).launch {
            combine(
                vm.herbarium,
                snapshotFlow { vm.labelPlacementEntryId },
                snapshotFlow { vm.cleanMode }
            ) { entries, placingId, clean -> Triple(entries, placingId, clean) }
                .collect { (entries, placingId, clean) ->
                    syncIdLabels(entries, placingId, clean)
                }
        }
        sceneEntitiesCreated = true
    }

    /** HUD entity: grabbable + resizable, re-pinned to the head each tick. */
    private fun createHudEntity(pose: Pose, visible: Boolean): Entity = Entity.create(
        Panel(R.id.panel_hud),
        Transform(pose),
        Visible(visible),
        Grabbable().apply {
            enabled = true
            type = GrabbableType.PIVOT_Y
            minHeight = 0.2f
            maxHeight = 3.0f
        },
        IsdkPanelResize().apply {
            enabled = true
            resizeMode = ResizeMode.Relayout
        }
    )

    /** Manage entity: grabbable + resizable like the HUD, static pose. */
    private fun createManageEntity(pose: Pose, visible: Boolean): Entity = Entity.create(
        Panel(R.id.panel_manage),
        Transform(pose),
        Visible(visible),
        Grabbable().apply {
            enabled = true
            type = GrabbableType.PIVOT_Y
            minHeight = 0.2f
            maxHeight = 3.0f
        },
        IsdkPanelResize().apply {
            enabled = true
            resizeMode = ResizeMode.Relayout
        }
    )

    /** Clean-mode tripwire: never grabbable, head-tracked while clean. */
    private fun createTripwireEntity(pose: Pose, visible: Boolean): Entity = Entity.create(
        Panel(R.id.panel_tripwire),
        Transform(pose),
        Visible(visible)
    )

    /**
     * Compact HUD entity: deliberately not grabbable — the compact
     * constants are the source of truth, tuned via test recordings.
     */
    private fun createCompactHudEntity(pose: Pose, visible: Boolean): Entity = Entity.create(
        Panel(R.id.panel_hud_vertical),
        Transform(pose),
        Visible(visible)
    )

    /** Viewfinder entity: never grabbable, resizable, or clickable. */
    private fun createViewfinderEntity(pose: Pose, visible: Boolean): Entity = Entity.create(
        Panel(R.id.panel_viewfinder),
        Transform(pose),
        Visible(visible)
    )

    /** Pushes one visibility snapshot to the panel entities. */
    private fun applyPanelVis(vis: PanelVis) {
        try {
            hudEntity?.setComponent(Visible(vis.hud))
            hudVerticalEntity?.setComponent(Visible(vis.hudCompact))
            manageEntity?.setComponent(Visible(vis.manage))
            tripwireEntity?.setComponent(Visible(vis.tripwire))
            viewfinderEntity?.setComponent(Visible(vis.viewfinder))
            hudTracked = vis.hud
            compactHudTracked = vis.hudCompact
            manageTracked = vis.manage
            viewfinderTracked = vis.viewfinder
            vm.verticalHudActive = vis.hudCompact
        } catch (e: Exception) {
            Log.e(TAG, "panel visibility update failed", e)
        }
    }

    /**
     * Sleep/wake restore: destroys and recreates every panel entity so the
     * SDK builds fresh native panel objects (the old ones died with the
     * stopped session and are never rebuilt otherwise). Runs only on
     * resumes after the first — onSceneReady owns cold start, so no
     * duplicates. Visibility restores from the surviving vm state, poses
     * from pre-destroy snapshots (moved panels return exactly), and tray
     * cards re-sync from the tray table (anchors persist in the database).
     * Resize state is the one thing not preserved (not readable back).
     */
    private fun recreatePanelEntities() {
        val vis = currentPanelVis(
            vm.settings.value, vm.cleanMode, vm.manageVisible, vm.hudScreen, vm.hideViewfinder
        )
        WakeLog.log(this, "recreate entry vis=$vis cards=${trayCards.size}")
        val hudPose = hudEntity?.tryGetComponent<Transform>()?.transform
        val managePose = manageEntity?.tryGetComponent<Transform>()?.transform
        val verticalPose = hudVerticalEntity?.tryGetComponent<Transform>()?.transform
        val viewfinderPose = viewfinderEntity?.tryGetComponent<Transform>()?.transform
        val tripwirePose = tripwireEntity?.tryGetComponent<Transform>()?.transform
        try { hudEntity?.destroy() } catch (e: Exception) {
            Log.e(TAG, "destroying HUD entity failed", e)
            WakeLog.log(this, "recreate destroy hud FAILED ${e.message}")
        }
        try { manageEntity?.destroy() } catch (e: Exception) {
            Log.e(TAG, "destroying manage entity failed", e)
            WakeLog.log(this, "recreate destroy manage FAILED ${e.message}")
        }
        try { hudVerticalEntity?.destroy() } catch (e: Exception) {
            Log.e(TAG, "destroying compact HUD entity failed", e)
            WakeLog.log(this, "recreate destroy compact FAILED ${e.message}")
        }
        try { viewfinderEntity?.destroy() } catch (e: Exception) {
            Log.e(TAG, "destroying viewfinder entity failed", e)
            WakeLog.log(this, "recreate destroy finder FAILED ${e.message}")
        }
        try { tripwireEntity?.destroy() } catch (e: Exception) {
            Log.e(TAG, "destroying tripwire entity failed", e)
            WakeLog.log(this, "recreate destroy tripwire FAILED ${e.message}")
        }
        for ((trayId, card) in trayCards) {
            try {
                card.entity.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "destroying tray card $trayId failed", e)
                WakeLog.log(this, "recreate destroy card=$trayId FAILED ${e.message}")
            }
        }
        trayCards.clear()
        for ((entryId, label) in idLabels) {
            try {
                label.entity.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "destroying ID label $entryId failed", e)
                WakeLog.log(this, "recreate destroy label=$entryId FAILED ${e.message}")
            }
        }
        idLabels.clear()
        // nextCardPanelId keeps incrementing: old runtime registrations
        // persist in the SDK map, so IDs must never be reused.
        hudWasGrabbed = false
        vm.dropCardTrayId = null
        vm.dropLabelEntryId = null

        hudEntity = createHudEntity(hudPose ?: hudSpawn, vis.hud)
        manageEntity = createManageEntity(managePose ?: manageSpawn, vis.manage)
        hudVerticalEntity = createCompactHudEntity(verticalPose ?: Pose(compactHudPos), vis.hudCompact)
        viewfinderEntity = createViewfinderEntity(viewfinderPose ?: Pose(viewfinderSpawn), vis.viewfinder)
        tripwireEntity = createTripwireEntity(tripwirePose ?: tripwireSpawn, vis.tripwire)
        applyPanelVis(vis)
        try {
            syncTrayCards(vm.trays.value, vm.placementMode, vm.cleanMode)
        } catch (e: Exception) {
            Log.e(TAG, "tray card restore failed", e)
            WakeLog.log(this, "recreate traySync FAILED ${e.message}")
        }
        try {
            syncIdLabels(vm.herbarium.value, vm.labelPlacementEntryId, vm.cleanMode)
        } catch (e: Exception) {
            Log.e(TAG, "ID label restore failed", e)
            WakeLog.log(this, "recreate labelSync FAILED ${e.message}")
        }
        WakeLog.log(
            this,
            "recreate done hud=${System.identityHashCode(hudEntity)} " +
                "manage=${System.identityHashCode(manageEntity)} cards=${trayCards.size}"
        )
    }

    /**
     * Arms the session watchdog: if no live session state arrives within
     * [delayMs], the session is presumed dead (sleep stops it and, on
     * this OS/SDK combo, it never restarts — no frames, no ticks, no
     * panels, no input) and the activity recreates itself for a fresh
     * session. Any previous watchdog is replaced.
     */
    private fun armSessionWatchdog(delayMs: Long) {
        sessionWatchdog?.cancel()
        WakeLog.log(this, "watchdog armed ${delayMs}ms")
        sessionWatchdog = CoroutineScope(Dispatchers.Main).launch {
            delay(delayMs)
            sessionWatchdog = null
            maybeAutoRecreate()
        }
    }

    /** Fires the watchdog: recreate unless the session recovered. */
    private fun maybeAutoRecreate() {
        val now = SystemClock.uptimeMillis()
        if (!shouldAutoRecreate(now, lastAutoRecreateMs, sessionAliveSinceResume)) {
            WakeLog.log(this, "recreate skipped alive=$sessionAliveSinceResume")
            return
        }
        if (isFinishing || isDestroyed) return
        lastAutoRecreateMs = now
        saveUiState()
        expectRecreateRestore = true
        WakeLog.log(this, "autoRecreate firing")
        recreate()
    }

    /** Snapshots resumable UI state for a recreation restore. */
    private fun saveUiState() {
        savedUi = SavedUiState(
            manageVisible = vm.manageVisible,
            cleanMode = vm.cleanMode,
            hudScreen = vm.hudScreen,
            hudTrayId = vm.hudTrayId,
            panelLocked = vm.panelLocked,
            placementMode = vm.placementMode,
            hideViewfinder = vm.hideViewfinder
        )
    }

    /** Restores UI state after an auto-recreate (one-shot, else fresh). */
    private fun restoreUiState() {
        val snapshot = savedUi
        savedUi = null
        if (!expectRecreateRestore || snapshot == null) {
            expectRecreateRestore = false
            return
        }
        expectRecreateRestore = false
        WakeLog.log(this, "ui restore $snapshot")
        vm.manageVisible = snapshot.manageVisible
        vm.cleanMode = snapshot.cleanMode
        vm.hudScreen = restoreHudScreen(snapshot.hudScreen)
        vm.hudTrayId = snapshot.hudTrayId
        vm.panelLocked = snapshot.panelLocked
        vm.placementMode = snapshot.placementMode
        vm.hideViewfinder = snapshot.hideViewfinder
    }

    /**
     * Reconciles floating tray cards with tray state: registers a panel and
     * creates an entity per tray on demand (true runtime registration —
     * no pre-registered pool), destroys cards for deleted trays, and applies
     * visibility, grabbability, and saved poses.
     */
    private fun syncTrayCards(trays: List<TrayEntity>, placing: Boolean, clean: Boolean) {
        // Every native touch below is guarded: one poisoned card entity
        // (e.g. a dangling handle) logs and skips instead of killing the
        // app, and the next emission retries the sync.
        try {
            trayCards.keys
                .filter { id -> trays.none { it.id == id } }
                .forEach { id ->
                    try {
                        trayCards.remove(id)?.entity?.destroy()
                    } catch (e: Exception) {
                        Log.e(TAG, "dropping tray card $id failed", e)
                        trayCards.remove(id)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "tray card cleanup failed", e)
        }
        if (!placing) {
            // Stale grab/drop state must never fire after the mode exits.
            trayCards.values.forEach { it.wasGrabbed = false }
            vm.dropCardTrayId = null
        }
        // Sticky ghost slots: list position must never drive the fan —
        // trays sort newest-first, so every save would reshuffle all
        // ghosts. Survivors keep their slots; only newcomers take free
        // ones (see assignGhostSlots).
        val ghostIds = trays.filter { !it.hasAnchor() }.map { it.id }
        val assigned = assignGhostSlots(ghostSlots, ghostIds)
        ghostSlots.clear()
        ghostSlots.putAll(assigned)
        for (tray in trays) {
            try {
                syncOneTrayCard(tray, ghostSlots[tray.id] ?: 0, placing, clean)
            } catch (e: Exception) {
                Log.e(TAG, "syncing tray card ${tray.id} failed", e)
            }
        }
    }

    /** Creates-on-demand and syncs one tray's floating card. See [syncTrayCards]. */
    private fun syncOneTrayCard(tray: TrayEntity, ghostSlot: Int, placing: Boolean, clean: Boolean) {
        val card = trayCards.getOrPut(tray.id) { createTrayCard(tray.id) }
        card.anchored = tray.hasAnchor()
        card.ghostSlot = ghostSlot
        card.entity.setComponent(
            Visible(trayCardVisible(card.anchored, placing, clean))
        )
        // Cards stay grabbable like the HUD — grab to move anytime (the
        // release tick saves the pose); placement mode only adds ghosts.
        // Set once, never mid-grab (swapping then would cancel the drag).
        if (!card.grabEnabled) {
            card.grabEnabled = true
            card.entity.setComponent(
                Grabbable().apply {
                    enabled = true
                    type = GrabbableType.PIVOT_Y
                    minHeight = 0.2f
                    maxHeight = 3.0f
                }
            )
        }
        // Never rewrite the pose mid-grab — the release tick owns the save.
        val grabbed = card.entity.tryGetComponent<Grabbable>()?.isGrabbed == true
        if (!grabbed) {
            val upright = scene.getViewerPose().q.removePitchAndRoll()
            if (card.anchored) {
                card.entity.setComponent(
                    Transform(
                        Pose(
                            Vector3(tray.anchorX!!, tray.anchorY!!, tray.anchorZ!!),
                            upright
                        )
                    )
                )
            } else if (placing) {
                val viewer = scene.getViewerPose()
                val ghostPose = viewer.times(ghostOffset(card.ghostSlot))
                card.entity.setComponent(Transform(Pose(ghostPose.t, upright)))
            }
        }
    }

    /** Registers one tray-card panel at runtime and creates its entity. */
    private fun createTrayCard(trayId: Long): TrayCard {
        val panelId = nextCardPanelId++
        registerPanel(
            ComposeViewPanelRegistration(
                registrationId = panelId,
                composeViewCreator = { _, context ->
                    ComposeView(context).apply {
                        setContent {
                            CompositionLocalProvider(
                                LocalOnBackPressedDispatcherOwner provides backDispatcherOwner,
                                LocalActivityResultRegistryOwner provides activityResultRegistryOwner
                            ) {
                                TrayCardRoute(vm, trayId)
                            }
                        }
                    }
                },
                settingsCreator = {
                    UIPanelSettings(
                        shape = QuadShapeOptions(width = 0.50f, height = 0.36f),
                        display = DpDisplayOptions(width = 500f, height = 360f)
                    )
                }
            )
        )
        val entity = Entity.create(
            Panel(panelId),
            Transform(Pose(Vector3(0f, 1.5f, 1.2f))),
            Visible(false),
            Grabbable().apply {
                enabled = false
                type = GrabbableType.PIVOT_Y
                minHeight = 0.2f
                maxHeight = 3.0f
            },
            // Resizable like the HUD (first sync also enables the grab).
            IsdkPanelResize().apply {
                enabled = true
                resizeMode = ResizeMode.Relayout
            }
        )
        return TrayCard(trayId = trayId, entity = entity)
    }

    /**
     * Reconciles floating ID labels with herbarium state. Mirrors
     * [syncTrayCards]: one panel per entry on demand, labels for deleted
     * entries destroyed, visibility and saved poses applied. Only the
     * entry being pinned shows a ghost; anchored labels render at their
     * saved anchors, so they stay put as the grower walks the rack.
     */
    private fun syncIdLabels(entries: List<HerbariumEntity>, placingId: Long?, clean: Boolean) {
        try {
            idLabels.keys
                .filter { id -> entries.none { it.id == id } }
                .forEach { id ->
                    try {
                        idLabels.remove(id)?.entity?.destroy()
                    } catch (e: Exception) {
                        Log.e(TAG, "dropping ID label $id failed", e)
                        idLabels.remove(id)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "ID label cleanup failed", e)
        }
        if (placingId == null) {
            // Stale grab/drop state must never fire after the mode exits.
            idLabels.values.forEach { it.wasGrabbed = false }
            vm.dropLabelEntryId = null
        }
        for (entry in entries) {
            try {
                syncOneIdLabel(entry, placingId == entry.id, clean)
            } catch (e: Exception) {
                Log.e(TAG, "syncing ID label ${entry.id} failed", e)
            }
        }
    }

    /** Creates-on-demand and syncs one entry's floating ID label. See [syncIdLabels]. */
    private fun syncOneIdLabel(entry: HerbariumEntity, placingThis: Boolean, clean: Boolean) {
        val label = idLabels.getOrPut(entry.id) { createIdLabel(entry.id) }
        label.anchored = entry.hasAnchor()
        label.entity.setComponent(
            Visible(idLabelVisible(label.anchored, placingThis, clean))
        )
        // Labels stay grabbable like tray cards — grab to move anytime
        // (the release tick saves the pose); Pin mode only adds the ghost.
        // Set once, never mid-grab (swapping then would cancel the drag).
        if (!label.grabEnabled) {
            label.grabEnabled = true
            label.entity.setComponent(
                Grabbable().apply {
                    enabled = true
                    type = GrabbableType.PIVOT_Y
                    minHeight = 0.2f
                    maxHeight = 3.0f
                }
            )
        }
        // Never rewrite the pose mid-grab — the release tick owns the save.
        val grabbed = label.entity.tryGetComponent<Grabbable>()?.isGrabbed == true
        if (!grabbed) {
            val upright = scene.getViewerPose().q.removePitchAndRoll()
            if (label.anchored) {
                label.entity.setComponent(
                    Transform(
                        Pose(
                            Vector3(entry.anchorX!!, entry.anchorY!!, entry.anchorZ!!),
                            upright
                        )
                    )
                )
            } else if (placingThis) {
                val viewer = scene.getViewerPose()
                val ghostPose = viewer.times(ghostOffset(0))
                label.entity.setComponent(Transform(Pose(ghostPose.t, upright)))
            }
        }
    }

    /** Registers one ID-label panel at runtime and creates its entity. */
    private fun createIdLabel(entryId: Long): IdLabel {
        val panelId = nextCardPanelId++
        registerPanel(
            ComposeViewPanelRegistration(
                registrationId = panelId,
                composeViewCreator = { _, context ->
                    ComposeView(context).apply {
                        setContent {
                            CompositionLocalProvider(
                                LocalOnBackPressedDispatcherOwner provides backDispatcherOwner,
                                LocalActivityResultRegistryOwner provides activityResultRegistryOwner
                            ) {
                                IdLabelRoute(vm, entryId)
                            }
                        }
                    }
                },
                settingsCreator = {
                    UIPanelSettings(
                        shape = QuadShapeOptions(width = 0.40f, height = 0.30f),
                        display = DpDisplayOptions(width = 400f, height = 300f)
                    )
                }
            )
        )
        val entity = Entity.create(
            Panel(panelId),
            Transform(Pose(Vector3(0f, 1.5f, 1.2f))),
            Visible(false),
            Grabbable().apply {
                enabled = false
                type = GrabbableType.PIVOT_Y
                minHeight = 0.2f
                maxHeight = 3.0f
            },
            IsdkPanelResize().apply {
                enabled = true
                resizeMode = ResizeMode.Relayout
            }
        )
        return IdLabel(entryId = entryId, entity = entity)
    }

    /** Viewer-space spawn pose for ghost [slot]: fanned out, ~1.1 m out. */
    private fun ghostOffset(slot: Int): Pose = Pose(
        Vector3((slot % 3 - 1) * 0.5f, -0.15f - (slot / 3) * 0.4f, 1.1f)
    )

    /**
     * Viewer-space rects on the HUD depth plane for every visible tray
     * card. Cards are read-only here — front ordering nudges the HUD
     * in depth, never them; their world anchors are the source of truth.
     */
    private fun visibleCardRects(viewer: Pose, hudDepthM: Float): List<ViewRect> {
        if (hudDepthM <= MIN_FRONT_DEPTH_M) return emptyList()
        val placing = vm.placementMode
        val clean = vm.cleanMode
        return trayCards.values.mapNotNull { card ->
            if (!trayCardVisible(card.anchored, placing, clean)) return@mapNotNull null
            val world = card.entity.tryGetComponent<Transform>()?.transform?.t
                ?: return@mapNotNull null
            projectToHudPlane(worldToViewer(viewer, world), hudDepthM)
        }
    }

    /**
     * One front-ordering step for a HUD/Manage entity: while its rect
     * overlaps any card rect, ease a small depth nudge toward the
     * viewer so depth ordering puts it on top; otherwise ease back to
     * zero. The panel never moves laterally. Head-tracked panels
     * re-derive home every tick ([unlockedHome]); static panels pass
     * null and reuse a stored anchor (captured on first sight,
     * refreshed on grab-release) so the world pose never drifts. A
     * parked panel already home skips the native call.
     */
    private fun applyHudFront(
        entity: Entity,
        viewer: Pose,
        unlockedHome: Pose?,
        halfWm: Float,
        halfHm: Float,
        state: HudFrontState
    ) {
        val home: Pose = if (unlockedHome != null && !vm.panelLocked) {
            state.anchor = null
            state.wasLocked = false
            unlockedHome
        } else {
            if (!state.wasLocked || state.anchor == null) {
                val cur = entity.tryGetComponent<Transform>()?.transform
                if (cur != null) {
                    val baked = viewerDepthNudgeToWorldDelta(viewer, state.depthM)
                    state.anchor = Pose(
                        Vector3(cur.t.x - baked.x, cur.t.y - baked.y, cur.t.z - baked.z),
                        cur.q
                    )
                }
                state.wasLocked = true
            }
            state.anchor ?: return
        }
        val viewCenter = worldToViewer(viewer, home.t)
        val overlapping = hudOverlapsCards(
            ViewRect(viewCenter.x, viewCenter.y, halfWm, halfHm),
            visibleCardRects(viewer, viewCenter.z)
        )
        state.depthM = lerpHudDepthM(state.depthM, if (overlapping) HUD_FRONT_NUDGE_M else 0f)
        val parked = unlockedHome == null || vm.panelLocked
        if (parked && state.depthM == 0f) return
        val push = viewerDepthNudgeToWorldDelta(viewer, state.depthM)
        entity.setComponent(
            Transform(
                Pose(
                    Vector3(home.t.x + push.x, home.t.y + push.y, home.t.z + push.z),
                    home.q
                )
            )
        )
    }

    override fun onSceneTick() {
        super.onSceneTick()
        val now = SystemClock.uptimeMillis()
        if (lastTickMs == 0L || now - lastTickMs > 5000L) {
            WakeLog.log(this, "tick gap=${now - lastTickMs}ms")
        }
        lastTickMs = now
        val hud = hudEntity ?: return
        val viewer = scene.getViewerPose()
        val grabbed = hud.tryGetComponent<Grabbable>()?.isGrabbed == true
        if (grabbed) {
            // Pinch-drag in progress — let the grab system move the panel.
            // Front ordering freezes: smoothing toward a dragged goal
            // would fight the hand, and the baked nudge is un-wound below.
            hudWasGrabbed = true
        } else {
            if (hudWasGrabbed) {
                // Just released — re-anchor the follow offset to the dropped
                // pose so the HUD keeps following from its new spot. The
                // live depth nudge is un-baked first: it is transient
                // and must not leak into the home or locked anchors, or
                // the panel would jump or stick after release.
                hudWasGrabbed = false
                val dropped = hud.tryGetComponent<Transform>()?.transform
                if (dropped != null) {
                    val baked = viewerDepthNudgeToWorldDelta(viewer, hudFrontState.depthM)
                    val unbaked = Pose(
                        Vector3(dropped.t.x - baked.x, dropped.t.y - baked.y, dropped.t.z - baked.z),
                        dropped.q
                    )
                    HUD_HEAD_OFFSET = viewer.inverse().times(unbaked)
                    hudFrontState.anchor = unbaked
                }
            }
            // Panel lock: the HUD stays world-locked (the Manage panel is
            // already static). Front ordering still applies in both modes,
            // from the head-follow home or the locked anchor; a hidden HUD
            // resets instead so its return starts fresh.
            if (!hudTracked) {
                hudFrontState.reset()
            } else {
                applyHudFront(
                    hud, viewer, viewer.times(HUD_HEAD_OFFSET),
                    HUD_HALF_W_M, HUD_HALF_H_M, hudFrontState
                )
            }
        }
        // 9:16 pair tracks the view while showing, so the guide always
        // matches the recording and the compact HUD stays glued inside
        // the frame. The compact HUD gets the same front ordering as the
        // main HUD (it is never grabbable, so no freeze/release logic).
        // Honors panel lock exactly like the main HUD: locked freezes
        // the pair rigidly in world space, nudge aside. Hidden
        // panels are not re-pinned — less native traffic per tick.
        if (compactHudTracked) {
            hudVerticalEntity?.let { compact ->
                applyHudFront(
                    compact, viewer, viewer.times(compactHudOffset),
                    HUD_COMPACT_HALF_W_M, HUD_COMPACT_HALF_H_M, compactFrontState
                )
            }
        } else {
            compactFrontState.reset()
        }
        // Manage panel: static except for user drags, same front
        // ordering vs cards (never head-tracked: anchor-only home).
        // Grabbable like the HUD, so the same freeze/release dance.
        val manage = manageEntity
        if (manage == null || !manageTracked) {
            manageFrontState.reset()
        } else if (manage.tryGetComponent<Grabbable>()?.isGrabbed == true) {
            manageWasGrabbed = true
        } else {
            if (manageWasGrabbed) {
                manageWasGrabbed = false
                manage.tryGetComponent<Transform>()?.transform?.let { dropped ->
                    val baked = viewerDepthNudgeToWorldDelta(viewer, manageFrontState.depthM)
                    manageFrontState.anchor = Pose(
                        Vector3(dropped.t.x - baked.x, dropped.t.y - baked.y, dropped.t.z - baked.z),
                        dropped.q
                    )
                }
            }
            applyHudFront(
                manage, viewer, null,
                MANAGE_HALF_W_M, MANAGE_HALF_H_M, manageFrontState
            )
        }
        if (!vm.panelLocked && viewfinderTracked) {
            viewfinderEntity?.setComponent(
                Transform(scene.getViewerPose().times(viewfinderOffset))
            )
        }
        // The tripwire always tracks the view while clean so a tap anywhere
        // in view restores the panels.
        if (vm.cleanMode) {
            tripwireEntity?.setComponent(
                Transform(scene.getViewerPose().times(TRIPWIRE_OFFSET))
            )
        }
        // Card upkeep runs every tick: anchored yaw can never go stale
        // (see cardTickScope); ghost glides stay placement-gated inside.
        tickTrayCards()
        tickIdLabels()
    }

    /**
     * Placement-mode per-frame work: unplaced ghosts track the view until
     * grabbed, and every grab-release (or ghost tap) persists the card's
     * world pose as the tray's anchor.
     */
    private fun tickTrayCards() {
        if (trayCards.isEmpty()) return
        // Scope matrix (see cardTickScope): anchored billboards refresh
        // every tick so they never go stale (a stale yaw plus a tray
        // save used to snap every anchored card at once); ghost glides
        // and grab persistence stay placement-only.
        val scope = cardTickScope(vm.placementMode)
        val viewer = scene.getViewerPose()
        val upright = viewer.q.removePitchAndRoll()
        for ((trayId, card) in trayCards) {
            val grabbed = card.entity.tryGetComponent<Grabbable>()?.isGrabbed == true
            // Cards always face the user upright (yaw-only): ghosts while
            // placing, anchored cards forever — readable from anywhere at
            // the rack, never tilted. Position is untouched; grabs win.
            if (!grabbed) {
                if (!card.anchored) {
                    if (scope.trackGhosts) {
                        val ghostPose = viewer.times(ghostOffset(card.ghostSlot))
                        card.entity.setComponent(Transform(Pose(ghostPose.t, upright)))
                    }
                } else if (scope.refreshAnchoredYaw) {
                    card.entity.tryGetComponent<Transform>()?.transform?.t?.let { p ->
                        card.entity.setComponent(
                            Transform(Pose(Vector3(p.x, p.y, p.z), upright))
                        )
                    }
                }
            }
            if (scope.persistGrabs) {
                if (grabbed) {
                    card.wasGrabbed = true
                } else if (card.wasGrabbed) {
                    card.wasGrabbed = false
                    saveCardPose(trayId, card)
                }
            }
        }
        // Ghost tap-to-drop: pinch on the ghost drops it in place.
        if (scope.persistGrabs) {
            vm.dropCardTrayId?.let { trayId ->
                vm.dropCardTrayId = null
                trayCards[trayId]?.let { saveCardPose(trayId, it) }
            }
        }
    }

    private fun saveCardPose(trayId: Long, card: TrayCard) {
        card.entity.tryGetComponent<Transform>()?.transform?.t?.let { p ->
            vm.setTrayAnchor(trayId, p.x, p.y, p.z)
        }
    }

    /**
     * Per-frame ID-label work, mirroring [tickTrayCards]: anchored labels
     * re-face the viewer every tick (yaw-only billboards, stable world
     * positions for walk-around viewing); the placement ghost tracks the
     * view, and every grab-release or ghost tap persists the world pose
     * as the entry's anchor.
     */
    private fun tickIdLabels() {
        if (idLabels.isEmpty()) return
        val placing = vm.labelPlacementEntryId != null
        val scope = cardTickScope(placing)
        val viewer = scene.getViewerPose()
        val upright = viewer.q.removePitchAndRoll()
        for ((entryId, label) in idLabels) {
            val grabbed = label.entity.tryGetComponent<Grabbable>()?.isGrabbed == true
            if (!grabbed) {
                if (!label.anchored) {
                    if (scope.trackGhosts && vm.labelPlacementEntryId == entryId) {
                        val ghostPose = viewer.times(ghostOffset(0))
                        label.entity.setComponent(Transform(Pose(ghostPose.t, upright)))
                    }
                } else if (scope.refreshAnchoredYaw) {
                    label.entity.tryGetComponent<Transform>()?.transform?.t?.let { p ->
                        label.entity.setComponent(
                            Transform(Pose(Vector3(p.x, p.y, p.z), upright))
                        )
                    }
                }
            }
            if (scope.persistGrabs) {
                if (grabbed) {
                    label.wasGrabbed = true
                } else if (label.wasGrabbed) {
                    label.wasGrabbed = false
                    saveLabelPose(entryId, label)
                }
            }
        }
        // Ghost tap-to-drop: pinch on the ghost drops it in place.
        if (scope.persistGrabs) {
            vm.dropLabelEntryId?.let { entryId ->
                vm.dropLabelEntryId = null
                idLabels[entryId]?.let { saveLabelPose(entryId, it) }
            }
        }
    }

    private fun saveLabelPose(entryId: Long, label: IdLabel) {
        label.entity.tryGetComponent<Transform>()?.transform?.t?.let { p ->
            vm.setHerbariumAnchor(entryId, p.x, p.y, p.z)
        }
        // The drop lands the label: placement ends once a pose is saved.
        vm.labelPlacementEntryId = null
    }

    companion object {
        private const val TAG = "QuestSprout"
        private val HAND_TRACKING_PERMISSIONS = arrayOf(
            "com.oculus.permission.HAND_TRACKING",
            "horizonos.permission.HAND_TRACKING"
        )
        private const val HAND_TRACKING_REQUEST_CODE = 41
        // HUD anchor in view space: 30 cm right, 14 cm up, 85 cm out.
        private var HUD_HEAD_OFFSET = Pose(Vector3(0.30f, 0.14f, 0.85f))
        // Tripwire anchor in view space: centered, 1 m out.
        private val TRIPWIRE_OFFSET = Pose(Vector3(0f, 0f, 1.0f))
        // Session watchdog delays: cold start reaches FOCUSED in ~1.5 s,
        // so 10 s without a live state means the session is dead; an
        // observed death state shortens the wait to 2 s.
        private const val WATCHDOG_MS = 10_000L
        private const val FAST_WATCHDOG_MS = 2_000L
        // Auto-recreate state (survives the recreation it triggers).
        private var lastAutoRecreateMs = 0L
        private var expectRecreateRestore = false
        private var savedUi: SavedUiState? = null
    }
}

/** One computed visibility snapshot for every mode-driven panel. */
internal data class PanelVis(
    val hud: Boolean,
    val hudCompact: Boolean,
    val manage: Boolean,
    val tripwire: Boolean,
    val viewfinder: Boolean
)

/**
 * Pure visibility matrix: the same snapshot drives the live visibility
 * loop and the sleep/wake restore.
 */
internal fun currentPanelVis(
    settings: AppSettings,
    cleanMode: Boolean,
    manageVisible: Boolean,
    hudScreen: HudScreen,
    hideViewfinder: Boolean
): PanelVis {
    val vertical = settings.verticalVideo
    return PanelVis(
        hud = !cleanMode && !vertical,
        hudCompact = !cleanMode && vertical,
        manage = manageVisible && !cleanMode,
        tripwire = cleanMode,
        viewfinder = verticalViewfinderVisible(
            vertical, cleanMode, manageVisible, hudScreen, hideViewfinder
        )
    )
}

/** Resumable UI state carried across an auto-recreate (same process). */
internal data class SavedUiState(
    val manageVisible: Boolean,
    val cleanMode: Boolean,
    val hudScreen: HudScreen,
    val hudTrayId: Long?,
    val panelLocked: Boolean,
    val placementMode: Boolean,
    val hideViewfinder: Boolean
)

/**
 * Auto-recreate gate: fire only for a dead session past the cooldown, so
 * a persistently broken session can never loop recreations.
 */
internal fun shouldAutoRecreate(
    nowMs: Long,
    lastRecreateMs: Long,
    sessionAliveSinceResume: Boolean,
    minIntervalMs: Long = 30_000L
): Boolean = !sessionAliveSinceResume && (nowMs - lastRecreateMs) >= minIntervalMs

/**
 * Only resumable HUD screens survive a recreation — mid-scan and result
 * screens reference in-memory analysis state, so they land on Home.
 */
internal fun restoreHudScreen(saved: HudScreen): HudScreen = when (saved) {
    is HudScreen.Home, is HudScreen.Identify -> saved
    else -> HudScreen.Home
}
