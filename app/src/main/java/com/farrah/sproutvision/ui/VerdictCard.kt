package com.farrah.sproutvision.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farrah.sproutvision.analyze.AnalysisResult
import com.farrah.sproutvision.analyze.SegmentationSource
import com.farrah.sproutvision.analyze.VerdictTrace
import com.farrah.sproutvision.analyze.formatVerdictTrace
import com.farrah.sproutvision.analyze.toggleTraceExpanded
import com.farrah.sproutvision.analyze.verdictTrace
import com.farrah.sproutvision.data.TrayEntity
import com.farrah.sproutvision.util.daysSince

/**
 * The post-scan verdict: big health score, top issue, analyzer confidence,
 * and variety + Day N. Fixed near-black/green palette (not theme-driven) so
 * it reads clearly in a phone screenshot of a landscape capture cropped to
 * 9:16 vertical for Shorts: oversized numerals, short lines, generous
 * padding, nothing essential near the left/right crop edges.
 */
@Composable
fun VerdictCard(
    tray: TrayEntity,
    result: AnalysisResult,
    modifier: Modifier = Modifier
) {
    val day = remember(tray.id, tray.seededAtEpochMs) { daysSince(tray.seededAtEpochMs) }
    val topIssue = result.issues.firstOrNull()
    var traceExpanded by remember { mutableStateOf(false) }
    val healthColor = when {
        result.healthScore >= 80 -> Color(0xFF7CFC00)
        result.healthScore >= 60 -> Color(0xFFFFB300)
        else -> Color(0xFFFF5252)
    }

    Card(
        modifier = modifier.fillMaxWidth().clickable { traceExpanded = toggleTraceExpanded(traceExpanded) },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D3311))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${tray.variety} · Day $day",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = tray.name,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFC8E6C9)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "${result.healthScore}",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                color = healthColor
            )
            Text(
                text = "TRAY HEALTH",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFC8E6C9)
            )
            // Trace sits right under the score — at the card's bottom it
            // fell below the HUD panel fold and read as missing.
            VerdictTraceBlock(
                trace = result.verdictTrace(),
                expanded = traceExpanded
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "TOP ISSUE",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFC8E6C9)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = topIssue ?: "None — looking good",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "CONFIDENCE",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFC8E6C9)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (result.confidence > 0) "${result.confidence}%" else "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
            if (result.samCoverage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (result.samSource == SegmentationSource.DEMO) {
                        "Demo-measured coverage: ${result.samCoverage}%"
                    } else {
                        "SAM-measured coverage: ${result.samCoverage}%"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
            if (result.isDemo) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "demo analysis",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFC8E6C9)
                )
            }
        }
    }
}

/**
 * Pinned verdict trace: models, SAM coverage, timestamp, image hash.
 * Read-only receipts; collapsed by default so the card layout is
 * unchanged until tapped. Same fields as the history trace.
 */
@Composable
private fun VerdictTraceBlock(
    trace: VerdictTrace,
    expanded: Boolean
) {
    val display = remember(trace) { formatVerdictTrace(trace) }
    Spacer(Modifier.height(8.dp))
    Text(
        text = if (expanded) "Verdict trace ▾" else "Verdict trace ▸",
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFFC8E6C9)
    )
    if (expanded) {
        Spacer(Modifier.height(4.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            VerdictTraceField("Models", display.models)
            VerdictTraceField("SAM coverage", display.coverage)
            VerdictTraceField("Scanned", display.timestamp)
            VerdictTraceField("Image hash", display.imageHash)
        }
    }
}

@Composable
private fun VerdictTraceField(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFC8E6C9)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
    }
}
