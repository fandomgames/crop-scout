package validation

import com.farrah.sproutvision.analyze.SAM_TRAY_CONCEPT
import com.farrah.sproutvision.analyze.SamClient
import com.farrah.sproutvision.analyze.SamException
import com.farrah.sproutvision.analyze.SamRecord
import com.farrah.sproutvision.analyze.SamSegmenter
import com.farrah.sproutvision.analyze.compositeFrameGrid
import com.farrah.sproutvision.analyze.escapeJson
import com.farrah.sproutvision.analyze.largestRecord
import com.farrah.sproutvision.analyze.samConcept
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess

/**
 * Golden-tray validation: runs SAM 3.1 over the 5 frozen basil-tray
 * photos and writes golden-validation.json for the in-app Validation
 * view. Mac-side only — never ships in the APK.
 *
 * Usage: META_API_KEY='...' ./gradlew :validation:validateGolden
 * Args: [golden-photos dir] [output golden-validation.json]
 *
 * By-eye numbers are frozen (GOLDEN_TRAYS.MD, pre-SAM) — never edit.
 */
data class GoldenShot(val id: String, val file: String, val byEye: Int, val note: String?)

val GOLDEN_SHOTS = listOf(
    GoldenShot("shot1-full", "shot1-full.jpg", 70, null),
    GoldenShot("shot2-uneven", "shot2-uneven.jpg", 40, null),
    GoldenShot(
        "shot3-gap", "shot3-gap.jpg", 5,
        "Framing includes edge plants, so the SAM % on this photo will " +
            "exceed the 5% by-eye gap-zone estimate; recorded as-is, do not force a match."
    ),
    GoldenShot("shot4-poorlight", "shot4-poorlight.jpg", 70, null),
    GoldenShot(
        "shot5-partial", "shot5-partial.jpg", 65,
        "Partial frame (80% of tray visible): visible-area % runs higher " +
            "than full-tray % — the partial-frame bias this shot is designed to catch."
    )
)

data class ShotOutcome(
    val shot: GoldenShot,
    val sam: Int?,
    val plants: Int?,
    val trays: Int?,
    /** Which step failed ("upload"/"tray"/"plants"/"decode"/"photo missing"), null when measured. */
    val failed: String?
)

private fun shortErr(e: Exception): String =
    (e.message ?: e.javaClass.simpleName).replace('\n', ' ').take(100)

/**
 * One shot: upload once, tray request, plant request (same file ID),
 * then coverage = union of plant instances ÷ largest tray instance.
 * Network calls are injected so tests run offline. Mirrors
 * MetaSamSegmenter step for step (that file is frozen) plus the
 * instance counts the JSON needs. Never throws for a shot — any
 * failure becomes a null measurement with [ShotOutcome.failed] set.
 */
suspend fun runShot(
    bytes: ByteArray,
    shot: GoldenShot,
    upload: suspend (ByteArray) -> String,
    records: suspend (fileId: String, concept: String) -> List<SamRecord>
): ShotOutcome {
    val fileId = try {
        upload(bytes)
    } catch (e: Exception) {
        return ShotOutcome(shot, null, null, null, "upload (${shortErr(e)})")
    }
    val trayRecords = try {
        records(fileId, SAM_TRAY_CONCEPT)
    } catch (e: Exception) {
        return ShotOutcome(shot, null, null, null, "tray (${shortErr(e)})")
    }
    val plantRecords = try {
        records(fileId, samConcept(growOut = false))
    } catch (e: Exception) {
        return ShotOutcome(shot, null, null, trayRecords.size, "plants (${shortErr(e)})")
    }
    return try {
        val (trayGrid, tw, th) = compositeFrameGrid(listOf(largestRecord(trayRecords)))
        val (plantGrid, pw, ph) = compositeFrameGrid(plantRecords)
        if (tw != pw || th != ph) {
            throw SamException("SAM 3.1 frame mismatch: tray ${tw}x${th}, plants ${pw}x${ph}.")
        }
        val result = SamSegmenter.fromMasks(trayGrid, plantGrid, tw, th)
        ShotOutcome(shot, result.coveragePercent, plantRecords.size, trayRecords.size, null)
    } catch (e: Exception) {
        ShotOutcome(shot, null, plantRecords.size, trayRecords.size, "decode (${shortErr(e)})")
    }
}

fun renderJson(outcomes: List<ShotOutcome>): String = buildString {
    append("{\"shots\":[")
    outcomes.forEachIndexed { i, o ->
        if (i > 0) append(",")
        val diff = if (o.sam != null) o.sam - o.shot.byEye else null
        append(
            "{\"id\":\"" + escapeJson(o.shot.id) + "\"," +
                "\"samCoverage\":" + (o.sam?.toString() ?: "null") + "," +
                "\"byEye\":" + o.shot.byEye + "," +
                "\"difference\":" + (diff?.toString() ?: "null") + "," +
                "\"plantInstances\":" + (o.plants?.toString() ?: "null") + "," +
                "\"trayInstances\":" + (o.trays?.toString() ?: "null") + "," +
                "\"note\":" + (if (o.shot.note == null) "null" else "\"" + escapeJson(o.shot.note) + "\"") +
                "}"
        )
    }
    append("]}")
}

fun renderTable(outcomes: List<ShotOutcome>): String = buildString {
    appendLine("| Shot | SAM % | By-eye % | Difference |")
    appendLine("|---|---|---|---|")
    for (o in outcomes) {
        val sam = o.sam?.toString() ?: "—"
        val diff = if (o.sam != null) "%+d".format(o.sam - o.shot.byEye) else "—"
        appendLine("| ${o.shot.id} | $sam | ${o.shot.byEye} | $diff |")
    }
}.trimEnd()

fun main(args: Array<String>) = runBlocking {
    val photosDir = File(args.getOrElse(0) { "golden-photos" })
    val outFile = File(args.getOrElse(1) { "app/src/main/assets/golden-validation.json" })
    val key = System.getenv("META_API_KEY").orEmpty()
    if (key.isBlank()) {
        System.err.println("ERROR: META_API_KEY is not set.")
        System.err.println("Run: export META_API_KEY='paste-your-key-here'")
        System.err.println("Then re-run :validation:validateGolden. The key is never logged or written.")
        exitProcess(2)
    }
    val client = SamClient(key)
    val outcomes = GOLDEN_SHOTS.map { shot ->
        val photo = File(photosDir, shot.file)
        if (!photo.isFile) {
            println("SKIP ${shot.id}: ${shot.file} not found in $photosDir")
            ShotOutcome(shot, null, null, null, "photo missing")
        } else {
            println("Measuring ${shot.id} (${photo.length() / 1024} KB)…")
            runShot(
                photo.readBytes(), shot,
                upload = { client.uploadPhoto(it) },
                records = { id, concept -> client.segmentRecords(id, concept) { println("  [$id] $it") } }
            )
        }
    }
    outFile.parentFile.mkdirs()
    outFile.writeText(renderJson(outcomes))
    println()
    println(renderTable(outcomes))
    println()
    println("Wrote $outFile (${outcomes.size} shots).")
    val missing = outcomes.filter { it.sam == null }
    if (missing.isEmpty()) {
        println("All shots measured.")
    } else {
        println("Unavailable:")
        missing.forEach { println("- ${it.shot.id} (${it.failed})") }
    }
}
