package com.farrah.sproutvision.analyze

/**
 * Union box of SAM records in 0-1000 normalized coords (origin
 * top-left). Pure so the headset's highlight math stays testable.
 */
internal fun trayBoxFromRecords(records: List<SamRecord>): TrayBox {
    require(records.isNotEmpty()) { "No records for a tray box." }
    val frameW = records[0].frameW
    val frameH = records[0].frameH
    val x0 = records.minOf { it.x1 } * 1000 / frameW
    val y0 = records.minOf { it.y1 } * 1000 / frameH
    val x1 = (records.maxOf { it.x2 } + 1) * 1000 / frameW
    val y1 = (records.maxOf { it.y2 } + 1) * 1000 / frameH
    return TrayBox(
        x0.coerceIn(0, 1000), y0.coerceIn(0, 1000),
        x1.coerceIn(0, 1000), y1.coerceIn(0, 1000)
    )
}

/**
 * The tray is the largest returned instance by box area (mask dims
 * always equal box dims in this protocol, so box area ranks masks).
 * Smaller instances are spurious edge detections — e.g. a neighboring
 * tray sliver — and are ignored. Plants are NOT filtered: coverage is
 * the union of every plant instance.
 */
internal fun largestRecord(records: List<SamRecord>): SamRecord {
    require(records.isNotEmpty()) { "No records to choose from." }
    return records.maxByOrNull { (it.x2 - it.x1 + 1) * (it.y2 - it.y1 + 1) }!!
}

/**
 * Real SAM 3.1 measurement: one request for the tray
 * ([SAM_TRAY_CONCEPT], largest instance wins), one for the plants
 * ([samConcept], all instances unioned), then coverage = union of
 * plant instances ÷ tray-mask pixels. SAM measures; Spark judges from
 * the summary. Any failure throws and the scan falls back to a
 * verdict without measurement.
 *
 * @param growOut grow-out trays segment bare "plants", microgreen trays "microgreens".
 */
class MetaSamSegmenter(
    private val apiKey: String,
    private val growOut: Boolean = false
) : Segmenter {

    override suspend fun segment(
        imageBytes: ByteArray,
        onEvent: (String) -> Unit
    ): Segmentation {
        val client = SamClient(apiKey)
        onEvent("Uploading photo…")
        val fileId = client.uploadPhoto(imageBytes)
        onEvent("Segmenting tray…")
        val rawTray = client.segmentRecords(fileId, SAM_TRAY_CONCEPT, onEvent)
        if (rawTray.size > 1) onEvent("Tray: largest of ${rawTray.size} instances")
        val trayRecords = listOf(largestRecord(rawTray))
        onEvent("Segmenting plants…")
        val plantConcept = samConcept(growOut)
        val plantRecords = client.segmentRecords(fileId, plantConcept, onEvent)
        val (trayGrid, trayW, trayH) = compositeFrameGrid(trayRecords)
        val (plantGrid, plantW, plantH) = compositeFrameGrid(plantRecords)
        if (trayW != plantW || trayH != plantH) {
            throw SamException(
                "SAM 3.1 frame mismatch: tray ${trayW}x${trayH}, plants ${plantW}x${plantH}."
            )
        }
        val result = SamSegmenter.fromMasks(trayGrid, plantGrid, trayW, trayH)
        val noun = if (plantRecords.size == 1) "object" else "objects"
        onEvent("SAM 3.1 measured ${plantRecords.size} $noun: ${result.summary}")
        return Segmentation(
            trayGrid = trayGrid,
            plantGrid = plantGrid,
            width = trayW,
            height = trayH,
            result = result,
            source = SegmentationSource.META,
            trayBox = trayBoxFromRecords(trayRecords)
        )
    }
}
