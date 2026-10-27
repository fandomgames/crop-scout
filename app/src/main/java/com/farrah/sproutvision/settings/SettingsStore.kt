package com.farrah.sproutvision.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "sprout_settings")

data class AppSettings(
    val geminiApiKey: String = "",
    /** Hackathon only: Meta Model API key (SAM 3.1 + Muse Spark). Never hardcoded. */
    val metaApiKey: String = "",
    /** Hackathon only: when true and a Meta key is set, use Meta instead of Gemini. */
    val useMetaAi: Boolean = false,
    /**
     * Kill-switch for SAM measurement. On (default) + Meta enabled =
     * segment before judging; off = skip segmentation entirely (no SAM
     * network calls) and judge Spark-only. Inert when Meta is off.
     */
    val useSamMeasurement: Boolean = true,
    /** When true, the scan screen shows a sample photo and scripted results (emulator testing). */
    val demoMode: Boolean = false,
    /** Black background with green text. */
    val darkMode: Boolean = false,
    /** Draw the detected-tray box over the captured photo. */
    val highlightTray: Boolean = true,
    /** Speak a one-line verdict after each scan completes. */
    val voiceReadout: Boolean = true,
    /** Play a soft chime the moment a scan completes (independent of voice). */
    val scanChime: Boolean = true,
    /** Vertical video mode: compact world-locked HUD for 9:16 capture. */
    val verticalVideo: Boolean = false,
    /** Recent successful demo-analysis durations (typical-time estimate). */
    val recentDemoMs: List<Long> = emptyList(),
    /** Recent successful Gemini-analysis durations (typical-time estimate). */
    val recentGeminiMs: List<Long> = emptyList(),
    /** Custom seed varieties remembered from saved trays (auto-save). */
    val customVarieties: List<String> = emptyList(),
    val notificationsEnabled: Boolean = true
)

class SettingsStore(private val context: Context) {

    private val keyApiKey = stringPreferencesKey("gemini_api_key")
    private val keyMetaApiKey = stringPreferencesKey("meta_api_key")
    private val keyUseMetaAi = booleanPreferencesKey("use_meta_ai")
    private val keyUseSam = booleanPreferencesKey("use_sam_measurement")
    private val keyDemoMode = booleanPreferencesKey("demo_mode")
    private val keyDarkMode = booleanPreferencesKey("dark_mode")
    private val keyHighlightTray = booleanPreferencesKey("highlight_tray")
    private val keyVoiceReadout = booleanPreferencesKey("voice_readout")
    private val keyScanChime = booleanPreferencesKey("scan_chime")
    private val keyVerticalVideo = booleanPreferencesKey("vertical_video")
    private val keyRecentDemoMs = stringPreferencesKey("recent_demo_ms")
    private val keyRecentGeminiMs = stringPreferencesKey("recent_gemini_ms")
    private val keyCustomVarieties = stringPreferencesKey("custom_varieties")
    private val keyNotifications = booleanPreferencesKey("notifications_enabled")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            geminiApiKey = prefs[keyApiKey].orEmpty(),
            metaApiKey = prefs[keyMetaApiKey].orEmpty(),
            useMetaAi = prefs[keyUseMetaAi] ?: false,
            useSamMeasurement = prefs[keyUseSam] ?: true,
            demoMode = prefs[keyDemoMode] ?: false,
            darkMode = prefs[keyDarkMode] ?: false,
            highlightTray = prefs[keyHighlightTray] ?: true,
            voiceReadout = prefs[keyVoiceReadout] ?: true,
            scanChime = prefs[keyScanChime] ?: true,
            verticalVideo = prefs[keyVerticalVideo] ?: false,
            recentDemoMs = parseMsList(prefs[keyRecentDemoMs]),
            recentGeminiMs = parseMsList(prefs[keyRecentGeminiMs]),
            customVarieties = parseCustomVarieties(prefs[keyCustomVarieties]),
            notificationsEnabled = prefs[keyNotifications] ?: true
        )
    }

    suspend fun setApiKey(key: String) {
        context.dataStore.edit { it[keyApiKey] = key }
    }

    suspend fun setMetaApiKey(key: String) {
        context.dataStore.edit { it[keyMetaApiKey] = key }
    }

    suspend fun setUseMetaAi(enabled: Boolean) {
        context.dataStore.edit { it[keyUseMetaAi] = enabled }
    }

    suspend fun setUseSamMeasurement(enabled: Boolean) {
        context.dataStore.edit { it[keyUseSam] = enabled }
    }

    suspend fun setDemoMode(enabled: Boolean) {
        context.dataStore.edit { it[keyDemoMode] = enabled }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[keyDarkMode] = enabled }
    }

    suspend fun setHighlightTray(enabled: Boolean) {
        context.dataStore.edit { it[keyHighlightTray] = enabled }
    }

    suspend fun setVoiceReadout(enabled: Boolean) {
        context.dataStore.edit { it[keyVoiceReadout] = enabled }
    }

    suspend fun setScanChime(enabled: Boolean) {
        context.dataStore.edit { it[keyScanChime] = enabled }
    }

    suspend fun setVerticalVideo(enabled: Boolean) {
        context.dataStore.edit { it[keyVerticalVideo] = enabled }
    }

    suspend fun setRecentDemoMs(values: List<Long>) {
        context.dataStore.edit { it[keyRecentDemoMs] = joinMsList(values) }
    }

    suspend fun setRecentGeminiMs(values: List<Long>) {
        context.dataStore.edit { it[keyRecentGeminiMs] = joinMsList(values) }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[keyNotifications] = enabled }
    }

    /** Remembers a custom variety; blanks, defaults, and dupes are no-ops. */
    suspend fun addCustomVariety(candidate: String, defaultNames: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[keyCustomVarieties] = joinCustomVarieties(
                mergeCustomVariety(
                    parseCustomVarieties(prefs[keyCustomVarieties]),
                    candidate,
                    defaultNames
                )
            )
        }
    }
}
