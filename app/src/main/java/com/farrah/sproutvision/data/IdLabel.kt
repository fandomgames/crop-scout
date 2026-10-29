package com.farrah.sproutvision.data

import com.farrah.sproutvision.analyze.IdentifyConfidence
import com.farrah.sproutvision.analyze.identifyBadgeText

/**
 * Pure rules for the anchored ID label (the floating equivalent of the
 * herbarium grid card). The label mirrors the tray-card pattern: compact
 * content at a persisted world-space anchor, tap to expand, ghost while
 * placing. ID labels never touch tray data — the anchor lives on the
 * herbarium entry itself.
 */

/** Compact label content: common name, short scientific name, confidence. */
data class IdLabelContent(
    val commonName: String,
    val scientificShort: String,
    val confidenceText: String,
    val isBestGuess: Boolean
)

fun idLabelContentOf(entry: HerbariumEntity): IdLabelContent {
    val confidence = IdentifyConfidence.fromName(entry.confidence)
    return IdLabelContent(
        commonName = entry.commonName,
        scientificShort = shortScientificName(entry.scientificName),
        confidenceText = identifyBadgeText(confidence),
        isBestGuess = confidence == IdentifyConfidence.LOW
    )
}

/**
 * Short scientific form for the compact label: "Ocimum basilicum" reads
 * "O. basilicum". Single words and blanks pass through unchanged.
 */
fun shortScientificName(scientificName: String): String {
    val parts = scientificName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (parts.size < 2) return scientificName.trim()
    return parts[0].first() + ". " + parts.drop(1).joinToString(" ")
}

/**
 * Whether an ID label's floating panel is shown: anchored labels always
 * (except in clean mode); the entry being pinned shows its ghost only
 * while its placement is active.
 */
fun idLabelVisible(hasAnchor: Boolean, placingThis: Boolean, cleanMode: Boolean): Boolean =
    !cleanMode && (hasAnchor || placingThis)

/** Tap-to-expand toggle for the floating label (tap again to collapse). */
fun nextExpandedLabel(currentExpandedId: Long?, tappedId: Long): Long? =
    if (currentExpandedId == tappedId) null else tappedId

/**
 * Pin-section button for a herbarium entry: anchored entries offer to
 * remove the label, unpinned entries offer to pin it. Unpin only clears
 * the anchor — the entry itself is never deleted.
 */
fun labelPinButton(hasAnchor: Boolean): String =
    if (hasAnchor) "Remove label" else "Pin label"
