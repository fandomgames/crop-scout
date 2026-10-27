package com.farrah.sproutvision.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.farrah.sproutvision.R
import com.farrah.sproutvision.analyze.computeFramingStats
import com.farrah.sproutvision.analyze.framingAdviceNow
import com.farrah.sproutvision.data.TrayEntity

/**
 * Quest 3 tray scanner. Same contract as the Android XR build's ScanScreen
 * ([onAnalyze] receives JPEG bytes), but the capture path is the Camera2 API
 * aimed at the headset's passthrough cameras — CameraX can't see those.
 *
 * Follows Meta's Passthrough Camera API requirements (Horizon OS v74+,
 * Quest 3/3S): both android.permission.CAMERA and
 * horizontos.permission.HEADSET_CAMERA, camera selected via Meta's vendor
 * tags, session streaming to both preview and capture surfaces.
 * See https://developers.meta.com/vr/documentation/spatial-sdk/spatial-sdk-pca-kotlin-api/
 *
 * In demo mode the camera is skipped and the bundled sample photo is
 * analyzed, exactly like the original.
 */

/**
 * Which HUD panel owns the passthrough camera: exactly one panel may open
 * it (a second openCamera disconnects the first session). The main panel
 * owns it in normal mode, the compact panel in 9:16 mode.
 */
fun scanCameraEnabled(panelIsCompact: Boolean, verticalHudActive: Boolean): Boolean =
    panelIsCompact == verticalHudActive
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    vm: SproutViewModel,
    tray: TrayEntity,
    demoMode: Boolean,
    analyzing: Boolean,
    onAnalyze: (ByteArray) -> Unit,
    onCancel: () -> Unit,
    cameraEnabled: Boolean = true
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan ${tray.name}") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)
        ) {
            if (demoMode) {
                Card(Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.placeholder_tray),
                        contentDescription = "Sample tray photo (demo)",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(220.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Demo mode: this sample tray photo stands in for a real " +
                        "capture. Results are scripted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onAnalyze(ByteArray(0)) },
                    enabled = !analyzing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (analyzing) "Analyzing…" else "Analyze sample photo")
                }
                if (analyzing) {
                    Spacer(Modifier.height(12.dp))
                    AnalyzeStatusSection(
                        stage = vm.analyzeStage,
                        startedAtMs = vm.analyzeStartedAtMs,
                        typicalMs = vm.typicalAnalyzeMs,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        events = vm.analyzeEvents
                    )
                }
                return@Column
            }

            // Meta requires BOTH permissions for passthrough camera access.
            val cameraPermissions = remember {
                arrayOf(
                    Manifest.permission.CAMERA,
                    "horizonos.permission.HEADSET_CAMERA"
                )
            }
            var permissionsGranted by remember {
                mutableStateOf(
                    cameraPermissions.all {
                        ContextCompat.checkSelfPermission(context, it) ==
                            PackageManager.PERMISSION_GRANTED
                    }
                )
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { grants ->
                permissionsGranted = grants.values.all { it }
            }

            LaunchedEffect(Unit) {
                if (!permissionsGranted) permissionLauncher.launch(cameraPermissions)
            }

            if (!permissionsGranted) {
                Text(
                    "Camera permission is required to scan trays.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { permissionLauncher.launch(cameraPermissions) }) {
                    Text("Grant camera permission")
                }
                return@Column
            }

            if (!cameraEnabled) {
                // This panel is hidden — the visible HUD owns the camera.
                // Opening it here too would disconnect the live session,
                // so this copy draws a quiet placeholder and opens nothing.
                Spacer(Modifier.weight(1f).fillMaxWidth())
                Text(
                    "Camera preview is on the active HUD.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            val camera = remember { QuestTrayCamera(context) }
            var capturing by remember { mutableStateOf(false) }
            var captureError by remember { mutableStateOf<String?>(null) }
            DisposableEffect(Unit) {
                camera.coachingListener = { advice -> vm.framingAdviceNow = advice }
                onDispose { vm.framingAdviceNow = null; camera.close() }
            }

            // Pre-capture guidance hides while analyzing — the status feed
            // takes its space, which also keeps the analyzing view fitting
            // the compact panel.
            if (!analyzing) {
                Text(
                    "Aim the headset at the tray, then capture.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                // Pre-capture coaching from this tray's last scan (if any).
                remember(tray.id) { vm.lastResultFor(tray.id) }?.let { last ->
                    FramingAdviceLine(
                        result = last,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            // Live viewfinder coaching over the aiming preview: warns while
            // framing ("Too close…", "Tray out of view…"), quiet when fine.
            // Hidden once analyzing starts (the status feed takes over).
            val liveAdvice = vm.framingAdviceNow
            if (!analyzing && liveAdvice != null) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.7f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            liveAdvice,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                st: SurfaceTexture, w: Int, h: Int
                            ) {
                                camera.open(st) { err -> captureError = err }
                            }
                            override fun onSurfaceTextureSizeChanged(
                                st: SurfaceTexture, w: Int, h: Int
                            ) = Unit
                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                camera.close()
                                return true
                            }
                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                        }
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
            captureError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(12.dp))
            if (analyzing) {
                // Analyzing view: live status feed plus a way out. The feed
                // shows each step as it happens (uploading, waiting, retry),
                // so a stall is visible instead of a blind 40s wait — and
                // Cancel drops the request immediately.
                AnalyzeStatusSection(
                    stage = vm.analyzeStage,
                    startedAtMs = vm.analyzeStartedAtMs,
                    typicalMs = vm.typicalAnalyzeMs,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    events = vm.analyzeEvents
                )
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { vm.cancelAnalyze(); vm.hudScreen = HudScreen.Home },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel scan")
                }
            } else {
                Button(
                    onClick = {
                        capturing = true
                        captureError = null
                        camera.capture(
                            onBytes = { bytes ->
                                capturing = false
                                onAnalyze(bytes)
                            },
                            onError = {
                                capturing = false
                                captureError = it
                            }
                        )
                    },
                    enabled = !capturing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (capturing) "Capturing…" else "Capture tray")
                }
            }
        }
    }
}

/**
 * Wild-plant capture for Identify mode. Reuses the same passthrough camera
 * flow as tray scanning ([QuestTrayCamera], same permissions, same vendor-tag
 * camera selection) but takes no tray, wires no tray framing coaching, and
 * hands JPEG bytes to the plant identifier instead of the tray analyzer.
 *
 * In demo mode the camera is skipped and the bundled sample photo is
 * identified with a scripted result, exactly like tray demo mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentifyScreen(
    vm: SproutViewModel,
    demoMode: Boolean,
    identifying: Boolean,
    onIdentify: (ByteArray) -> Unit,
    onCancel: () -> Unit,
    cameraEnabled: Boolean = true
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Identify wild plant") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)
        ) {
            if (demoMode) {
                Card(Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.placeholder_tray),
                        contentDescription = "Sample photo (demo)",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(220.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Demo mode: this sample photo stands in for a real " +
                        "capture. The identification is scripted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onIdentify(ByteArray(0)) },
                    enabled = !identifying,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (identifying) "Identifying…" else "Identify sample photo")
                }
                if (identifying) {
                    Spacer(Modifier.height(12.dp))
                    AnalyzeStatusSection(
                        stage = vm.identifyStage,
                        startedAtMs = vm.identifyStartedAtMs,
                        typicalMs = null,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        events = vm.identifyEvents
                    )
                }
                return@Column
            }

            // Meta requires BOTH permissions for passthrough camera access.
            val cameraPermissions = remember {
                arrayOf(
                    Manifest.permission.CAMERA,
                    "horizonos.permission.HEADSET_CAMERA"
                )
            }
            var permissionsGranted by remember {
                mutableStateOf(
                    cameraPermissions.all {
                        ContextCompat.checkSelfPermission(context, it) ==
                            PackageManager.PERMISSION_GRANTED
                    }
                )
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { grants ->
                permissionsGranted = grants.values.all { it }
            }

            LaunchedEffect(Unit) {
                if (!permissionsGranted) permissionLauncher.launch(cameraPermissions)
            }

            if (!permissionsGranted) {
                Text(
                    "Camera permission is required to identify plants.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { permissionLauncher.launch(cameraPermissions) }) {
                    Text("Grant camera permission")
                }
                return@Column
            }

            if (!cameraEnabled) {
                // This panel is hidden — the visible HUD owns the camera.
                // Opening it here too would disconnect the live session,
                // so this copy draws a quiet placeholder and opens nothing.
                Spacer(Modifier.weight(1f).fillMaxWidth())
                Text(
                    "Camera preview is on the active HUD.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            val camera = remember { QuestTrayCamera(context) }
            var capturing by remember { mutableStateOf(false) }
            var captureError by remember { mutableStateOf<String?>(null) }
            // No framing coaching: that judges tray framing, meaningless here.
            DisposableEffect(Unit) { onDispose { camera.close() } }

            if (!identifying) {
                Text(
                    "Aim the headset at the plant, then capture.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                st: SurfaceTexture, w: Int, h: Int
                            ) {
                                camera.open(st) { err -> captureError = err }
                            }
                            override fun onSurfaceTextureSizeChanged(
                                st: SurfaceTexture, w: Int, h: Int
                            ) = Unit
                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                camera.close()
                                return true
                            }
                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                        }
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
            captureError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            vm.identifyError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(12.dp))
            if (identifying) {
                AnalyzeStatusSection(
                    stage = vm.identifyStage,
                    startedAtMs = vm.identifyStartedAtMs,
                    typicalMs = null,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    events = vm.identifyEvents
                )
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { vm.cancelIdentify(); vm.hudScreen = HudScreen.Home },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel identification")
                }
            } else {
                Button(
                    onClick = {
                        capturing = true
                        captureError = null
                        camera.capture(
                            onBytes = { bytes ->
                                capturing = false
                                onIdentify(bytes)
                            },
                            onError = {
                                capturing = false
                                captureError = it
                            }
                        )
                    },
                    enabled = !capturing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (capturing) "Capturing…" else "Capture plant")
                }
            }
        }
    }
}

/**
 * Camera2 still-capture helper targeting the Quest 3 passthrough cameras,
 * following Meta's Passthrough Camera API sample: the camera is selected via
 * Meta's vendor tags (source == 0 is passthrough), and one repeating preview
 * request streams to both the preview surface and the JPEG ImageReader, so a
 * capture is just the latest frame — no separate still-capture dance.
 */
private class QuestTrayCamera(private val context: Context) {

    companion object {
        private const val TAG = "QuestTrayCamera"
        // Meta vendor tags — https://developers.meta.com/vr/documentation/spatial-sdk/spatial-sdk-pca-kotlin-api/
        private const val KEY_CAMERA_SOURCE = "com.meta.extra_metadata.camera_source"
        private const val KEY_CAMERA_POSITION = "com.meta.extra_metadata.position"
        private const val SOURCE_PASSTHROUGH = 0
        private const val POSITION_LEFT = 0
        private const val CAPTURE_TIMEOUT_MS = 12_000L
        private const val COACHING_INTERVAL_MS = 750L
    }

    private val cameraManager: CameraManager =
        context.getSystemService(CameraManager::class.java)
    private val workerThread = HandlerThread("quest-tray-camera").apply { start() }
    private val worker = Handler(workerThread.looper)

    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var cameraIdInUse: String? = null

    @Volatile private var latestBytes: ByteArray? = null
    private var pendingCapture: ((ByteArray) -> Unit)? = null
    private var pendingError: ((String) -> Unit)? = null
    /** Live framing advice, or null when framing looks fine. Runs on worker. */
    var coachingListener: ((String?) -> Unit)? = null
    private var lastCoachingMs = 0L
    private val timeoutRunnable = Runnable {
        pendingCapture?.let {
            pendingCapture = null
            pendingError?.invoke(
                "Capture timed out — no frame from camera $cameraIdInUse. " +
                    "Is passthrough enabled and are both camera permissions granted?"
            )
            pendingError = null
        }
    }

    /**
     * Finds the passthrough camera via Meta's vendor tags (source == 0),
     * preferring the left one. Falls back to the first camera id.
     */
    private fun findPassthroughCamera(): String? {
        val sourceKey = CameraCharacteristics.Key(KEY_CAMERA_SOURCE, Int::class.java)
        val positionKey = CameraCharacteristics.Key(KEY_CAMERA_POSITION, Int::class.java)
        var fallback: String? = null
        try {
            for (id in cameraManager.cameraIdList) {
                val chars = try {
                    cameraManager.getCameraCharacteristics(id)
                } catch (e: Exception) {
                    Log.w(TAG, "couldn't read characteristics for camera $id", e)
                    continue
                }
                val source = try { chars.get(sourceKey) } catch (_: Exception) { null }
                val position = try { chars.get(positionKey) } catch (_: Exception) { null }
                Log.i(TAG, "camera $id: source=$source position=$position")
                if (source == SOURCE_PASSTHROUGH) {
                    if (position == POSITION_LEFT) return id
                    if (fallback == null) fallback = id
                }
            }
            if (fallback == null) {
                fallback = cameraManager.cameraIdList.firstOrNull()
                Log.w(TAG, "no passthrough camera found; falling back to $fallback")
            }
        } catch (e: Exception) {
            Log.e(TAG, "camera enumeration failed", e)
        }
        return fallback
    }

    @SuppressLint("MissingPermission")
    fun open(surfaceTexture: SurfaceTexture, onError: (String) -> Unit) {
        val cameraId = findPassthroughCamera()
        if (cameraId == null) {
            onError("No camera found on this device.")
            return
        }
        cameraIdInUse = cameraId
        val reader = ImageReader.newInstance(1280, 960, ImageFormat.JPEG, 2)
        reader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                latestBytes = bytes
                pendingCapture?.let { cb ->
                    pendingCapture = null
                    pendingError = null
                    worker.removeCallbacks(timeoutRunnable)
                    cb(bytes)
                }
                // Live framing coach, throttled: runs after capture serving
                // so coaching decode can never delay a capture.
                coachingListener?.let { listener ->
                    val now = System.currentTimeMillis()
                    if (now - lastCoachingMs >= COACHING_INTERVAL_MS) {
                        lastCoachingMs = now
                        listener(coachFrame(bytes))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "frame read failed", e)
            } finally {
                image.close()
            }
        }, worker)
        imageReader = reader

        surfaceTexture.setDefaultBufferSize(1280, 960)
        val previewSurface = Surface(surfaceTexture)
        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(d: CameraDevice) {
                    device = d
                    d.createCaptureSession(
                        listOf(previewSurface, reader.surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(s: CameraCaptureSession) {
                                session = s
                                // Meta's sample streams to BOTH surfaces in one
                                // repeating request.
                                val req = d.createCaptureRequest(
                                    CameraDevice.TEMPLATE_PREVIEW
                                )
                                req.addTarget(previewSurface)
                                req.addTarget(reader.surface)
                                s.setRepeatingRequest(req.build(), null, worker)
                                Log.i(TAG, "capture session configured on camera $cameraId")
                            }

                            override fun onConfigureFailed(s: CameraCaptureSession) {
                                onError(
                                    "Camera session failed to configure " +
                                        "(camera $cameraId)."
                                )
                            }
                        },
                        worker
                    )
                }

                override fun onDisconnected(d: CameraDevice) {
                    d.close()
                    device = null
                }

                override fun onError(d: CameraDevice, error: Int) {
                    d.close()
                    device = null
                    onError("Camera error $error (camera $cameraId).")
                }
            }, worker)
        } catch (e: Exception) {
            onError(e.message ?: "Could not open camera $cameraId.")
        }
    }

    /**
     * Captures the latest frame. If the camera is already streaming this
     * returns immediately; otherwise it waits for the next frame up to the
     * timeout instead of hanging forever.
     */
    fun capture(onBytes: (ByteArray) -> Unit, onError: (String) -> Unit) {
        if (device == null || session == null || imageReader == null) {
            onError("Camera isn't ready yet — wait a second and retry.")
            return
        }
        latestBytes?.let { onBytes(it); return }
        pendingCapture = onBytes
        pendingError = onError
        worker.removeCallbacks(timeoutRunnable)
        worker.postDelayed(timeoutRunnable, CAPTURE_TIMEOUT_MS)
    }

    /**
     * One coaching pass over a JPEG frame: downsampled decode, pixel stats,
     * framing rules. Null when framing looks fine or decode fails.
     */
    private fun coachFrame(jpeg: ByteArray): String? {
        return try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
            val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts)
                ?: return null
            val w = bitmap.width
            val h = bitmap.height
            if (w == 0 || h == 0) return null
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            framingAdviceNow(computeFramingStats(pixels, w, h, stride = 2))
        } catch (_: Exception) {
            null
        }
    }

    fun close() {
        try {
            worker.removeCallbacks(timeoutRunnable)
            session?.close()
            device?.close()
            imageReader?.close()
        } catch (_: Exception) { }
        session = null
        device = null
        imageReader = null
        latestBytes = null
        pendingCapture = null
        pendingError = null
        coachingListener = null
        workerThread.quitSafely()
    }
}
