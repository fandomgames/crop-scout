package com.farrah.sproutvision.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.farrah.sproutvision.data.TrayEntity
import com.farrah.sproutvision.settings.AppSettings

private enum class KeyKbField { Gemini, Meta }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onApiKey: (String) -> Unit,
    onMetaApiKey: (String) -> Unit = {},
    onUseMetaAi: (Boolean) -> Unit = {},
    onUseSamMeasurement: (Boolean) -> Unit = {},
    onDemoMode: (Boolean) -> Unit,
    onDarkMode: (Boolean) -> Unit,
    onHighlightTray: (Boolean) -> Unit,
    onVoiceReadout: (Boolean) -> Unit,
    onScanChime: (Boolean) -> Unit,
    onVerticalVideo: (Boolean) -> Unit,
    hideViewfinder: Boolean,
    onHideViewfinder: (Boolean) -> Unit,
    onNotifications: (Boolean) -> Unit,
    trays: List<TrayEntity>,
    onDeleteTray: (TrayEntity) -> Unit,
    onClearData: () -> Unit,
    onLoadDemoTrays: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    var apiKeyInput by remember(settings.geminiApiKey) { mutableStateOf(settings.geminiApiKey) }
    var metaApiKeyInput by remember(settings.metaApiKey) { mutableStateOf(settings.metaApiKey) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var removingTray by remember { mutableStateOf<TrayEntity?>(null) }
    // In-panel keyboard target: the system keyboard never appears in these
    // panels, so focusing a key field raises PanelKeyboard instead. Buttons
    // don't steal touch focus, so Done (or Save key) dismisses it.
    var kbTarget by remember { mutableStateOf<KeyKbField?>(null) }
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close menu")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
            ) {
                Text("Gemini API key", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Get a free key at Google AI Studio (aistudio.google.com → Get API key). " +
                        "Required for real tray analysis on the glasses.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                        .onFocusChanged { if (it.isFocused) kbTarget = KeyKbField.Gemini },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        onApiKey(apiKeyInput)
                        kbTarget = null
                        focusManager.clearFocus()
                    },
                    enabled = apiKeyInput != settings.geminiApiKey
                ) {
                    Text("Save key")
                }

                Spacer(Modifier.height(24.dp))
                Text("Meta Model API key (hackathon)", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "For SAM 3.1 + Muse Spark. Get a key at dev.meta.ai. Never share it in a video or repo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = metaApiKeyInput,
                    onValueChange = { metaApiKeyInput = it },
                    label = { Text("Meta API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                        .onFocusChanged { if (it.isFocused) kbTarget = KeyKbField.Meta },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        onMetaApiKey(metaApiKeyInput)
                        kbTarget = null
                        focusManager.clearFocus()
                    },
                    enabled = metaApiKeyInput != settings.metaApiKey
                ) {
                    Text("Save Meta key")
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Use Meta AI (Crop Scout)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Off = Gemini fallback. On = SAM + Muse Spark, only when a Meta key is saved.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = settings.useMetaAi,
                        onCheckedChange = onUseMetaAi
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Use SAM measurement", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Off = skip segmentation entirely (no SAM calls), Spark-only verdict. Only takes effect when Meta AI is on.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = settings.useSamMeasurement,
                        onCheckedChange = onUseSamMeasurement,
                        enabled = settings.useMetaAi
                    )
                }

                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Demo mode", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Scripted results — no key or camera needed. Keep ON in the emulator.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = settings.demoMode,
                        onCheckedChange = onDemoMode
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Reminders", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Daily harvest-window and tray-check notifications.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = settings.notificationsEnabled,
                        onCheckedChange = onNotifications
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Dark mode", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Black background with green text.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = settings.darkMode,
                        onCheckedChange = onDarkMode
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tray highlight", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Draw a box around the detected tray on the result photo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = settings.highlightTray,
                        onCheckedChange = onHighlightTray
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Voice readout", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Speak a one-line verdict after each scan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = settings.voiceReadout,
                        onCheckedChange = onVoiceReadout
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Scan chime", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Play a soft tone the moment each scan completes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = settings.scanChime,
                        onCheckedChange = onScanChime
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Vertical video", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Compact HUD tucked top-right for 9:16 capture.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = settings.verticalVideo,
                        onCheckedChange = onVerticalVideo
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Hide frame guide", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "9:16 mode without the frame lines (compact HUD stays).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = hideViewfinder,
                        onCheckedChange = onHideViewfinder,
                        enabled = settings.verticalVideo
                    )
                }

                Spacer(Modifier.height(24.dp))
                Text("Remove trays", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Delete trays you're done with — after you've harvested " +
                        "and cleared them from the rack.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                if (trays.isEmpty()) {
                    Text(
                        "No trays yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    trays.forEach { tray ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(tray.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${tray.variety} · ${tray.currentStage.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { removingTray = tray }) {
                                Text("Remove", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                removingTray?.let { target ->
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Remove ${target.name}?", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Deletes the tray and its stage history. This can't be undone.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = { removingTray = null }) { Text("Cancel") }
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { removingTray = null; onDeleteTray(target) }) {
                                    Text("Remove", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
                if (showClearConfirm) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Clear all data?", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Deletes every tray and its stage history. This can't be undone.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { showClearConfirm = false; onClearData() }) {
                                    Text("Clear", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = { showClearConfirm = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear all tray data")
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "Sample data for trying the app: basil + kale. " +
                        "Loads only into an empty tray list — never touches existing trays.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onLoadDemoTrays,
                    enabled = trays.isEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Load demo trays")
                }
            }
            if (kbTarget != null) {
                Spacer(Modifier.height(8.dp))
                PanelKeyboard(
                    onKey = { key ->
                        when (kbTarget) {
                            KeyKbField.Gemini -> apiKeyInput = panelKeyboardEdit(apiKeyInput, key)
                            KeyKbField.Meta -> metaApiKeyInput = panelKeyboardEdit(metaApiKeyInput, key)
                            null -> Unit
                        }
                    },
                    onDone = { kbTarget = null; focusManager.clearFocus() }
                )
            }
        }
    }
}
