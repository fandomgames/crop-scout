package com.farrah.sproutvision.analyze

/**
 * Measures a tray photo: tray mask + plant masks → coverage.
 * Mirrors the [PlantAnalyzer] pattern (Demo/Meta implementations, the
 * ViewModel selects by key presence). Implementations must do their work
 * off the main thread. Any failure throws — the caller falls back to a
 * verdict without measurement, never an invented number.
 */
interface Segmenter {
    suspend fun segment(imageBytes: ByteArray, onEvent: (String) -> Unit): Segmentation
}
