package com.farrah.sproutvision.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** One stage entry parsed from the bundled seed file. */
data class SeedStage(
    val stage: GrowthStage,
    val timestampEpochMs: Long,
    val note: String
)

/** One tray parsed from the bundled seed file (assets/sproutvision-trays.json). */
data class SeedTray(
    val name: String,
    val variety: String,
    val seededAtEpochMs: Long,
    val expectedDaysToHarvest: Int,
    val notes: String,
    val currentStage: GrowthStage,
    val stages: List<SeedStage>
)

/**
 * Loads the real-tray seed data bundled with the app.
 *
 * Deprecated: seeding now comes from [DefaultTrays], which computes its
 * dates in code so an empty table always re-seeds. Kept for reference.
 */
@Deprecated("Seeding moved to DefaultTrays; this asset loader is unused.")
object TraySeedLoader {
    private const val ASSET_NAME = "sproutvision-trays.json"

    suspend fun load(context: Context): List<SeedTray> = withContext(Dispatchers.IO) {
        val json = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        val trays = JSONObject(json).getJSONArray("trays")
        buildList {
            for (i in 0 until trays.length()) {
                val t = trays.getJSONObject(i)
                val history = t.getJSONArray("stageHistory")
                val stages = buildList {
                    for (j in 0 until history.length()) {
                        val s = history.getJSONObject(j)
                        add(
                            SeedStage(
                                stage = GrowthStage.fromName(s.getString("stage")),
                                timestampEpochMs = s.getLong("timestampEpochMs"),
                                note = s.optString("note", "")
                            )
                        )
                    }
                }
                add(
                    SeedTray(
                        name = t.getString("name"),
                        variety = t.getString("variety"),
                        seededAtEpochMs = t.getLong("seededAtEpochMs"),
                        expectedDaysToHarvest = t.getInt("expectedDaysToHarvest"),
                        notes = t.optString("notes", ""),
                        currentStage = GrowthStage.fromName(t.getString("currentStage")),
                        stages = stages
                    )
                )
            }
        }
    }
}
