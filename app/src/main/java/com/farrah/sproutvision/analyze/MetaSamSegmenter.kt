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
 * The tray is the instance contributing the most tray-mask pixels:
 * decoded ones, scaled to box exactly as composited (each record
 * composited alone, so the rank is its real contribution).
 * Box area does NOT rank masks — a spurious near-frame box with a
 * sparse mask outranks the true tray by area while carrying a
 * fraction of its pixels. Smaller contributors are spurious edge
 * detections — e.g. a neighboring tray sliver — and are ignored.
 * Selection only — measurement divides by the winner's box
 * ([SamSegmenter.fromBox]), since the mask itself is rim-only.
 * Plants are NOT filtered: coverage is the union of every plant
 * instance, clipped to the tray box.
 */
internal fun largestRecord(records: List<SamRecord>): SamRecord {
    require(records.isNotEmpty()) { "No records to choose from." }
    return records.maxByOrNull {
        compositeFrameGrid(listOf(it)).first.count { it == 1 }
    }!!
}

/**
 * Real SAM 3.1 measurement: one request for the tray
 * ([SAM_TRAY_CONCEPT], largest instance wins), one for the plants
 * ([samConcept], all instances unioned), then coverage = plant
 * pixels clipped to the tray box ÷ box pixels (the tray mask is
 * rim-only, so mask pixels would understate the tray). SAM measures;
 * Spark judges from the summary. Any failure throws and the scan
 * falls back to a verdict without measurement.
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
        val tray = largestRecord(rawTray)
        onEvent("Segmenting plants…")
        val plantConcept = samConcept(growOut)
        val plantRecords = client.segmentRecords(fileId, plantConcept, onEvent)
        val (plantGrid, plantW, plantH) = compositeFrameGrid(plantRecords)
        if (tray.frameW != plantW || tray.frameH != plantH) {
            throw SamException(
                "SAM 3.1 frame mismatch: tray ${tray.frameW}x${tray.frameH}, plants ${plantW}x${plantH}."
            )
        }
        val result = SamSegmenter.fromBox(
            plantGrid, plantW, plantH, tray.x1, tray.y1, tray.x2, tray.y2
        )
        val noun = if (plantRecords.size == 1) "object" else "objects"
        onEvent("SAM 3.1 measured ${plantRecords.size} $noun: ${result.summary}")
        return Segmentation(
            trayGrid = IntArray(plantW * plantH) { i ->
                val x = i % plantW
                val y = i / plantW
                if (x in tray.x1..tray.x2 && y in tray.y1..tray.y2) 1 else 0
            },
            plantGrid = plantGrid,
            width = plantW,
            height = plantH,
            result = result,
            source = SegmentationSource.META,
            trayBox = trayBoxFromRecords(listOf(tray))
        )
    }
}
