package com.farrah.sproutvision.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Splits a "; "-joined issues string (as stored on scans and baselines). */
fun splitIssues(joined: String): List<String> =
    joined.split(";").map { it.trim() }.filter { it.isNotBlank() }

/** Before/after issue comparison: [resolved] cleared, [new] appeared, [ongoing] in both. */
data class IssueDelta(
    val resolved: List<String>,
    val ongoing: List<String>,
    val new: List<String>
)

/** Compares issue lists case-insensitively, keeping the original display text. */
fun diffIssues(before: List<String>, now: List<String>): IssueDelta {
    val beforeNorm = before.map { it.lowercase() }.toSet()
    val nowNorm = now.map { it.lowercase() }.toSet()
    return IssueDelta(
        resolved = before.filter { it.lowercase() !in nowNorm },
        ongoing = now.filter { it.lowercase() in beforeNorm },
        new = now.filter { it.lowercase() !in beforeNorm }
    )
}

/**
 * Before/after delta view: baseline health → current health side by side,
 * with resolved/new issue flags. Same high-contrast fixed palette as
 * [VerdictCard] so it survives the 9:16 Shorts crop.
 */
@Composable
fun BaselineDeltaCard(
    beforeHealth: Int,
    beforeIssues: List<String>,
    beforeLabel: String,
    nowHealth: Int,
    nowIssues: List<String>,
    nowLabel: String,
    modifier: Modifier = Modifier
) {
    val delta = diffIssues(beforeIssues, nowIssues)
    val change = nowHealth - beforeHealth
    val changeColor = when {
        change > 0 -> Color(0xFF7CFC00)
        change < 0 -> Color(0xFFFF5252)
        else -> Color(0xFFC8E6C9)
    }
    val changeText = remember(change) {
        when {
            change > 0 -> "+$change"
            else -> "$change"
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D3311))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "BEFORE → AFTER",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFC8E6C9)
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DeltaColumn(
                    label = beforeLabel,
                    caption = "BASELINE",
                    health = beforeHealth,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = changeText,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = changeColor
                )
                DeltaColumn(
                    label = nowLabel,
                    caption = "NOW",
                    health = nowHealth,
                    modifier = Modifier.weight(1f)
                )
            }
            if (delta.resolved.isNotEmpty() || delta.new.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    delta.resolved.forEach { issue ->
                        Text(
                            text = "✓ Resolved: $issue",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF7CFC00)
                        )
                    }
                    delta.new.forEach { issue ->
                        Text(
                            text = "! New: $issue",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFFB300)
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (nowIssues.isEmpty()) "No issues before or after"
                    else "Same issues as baseline",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFC8E6C9)
                )
            }
        }
    }
}

@Composable
private fun DeltaColumn(
    label: String,
    caption: String,
    health: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = caption,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFC8E6C9)
        )
        Text(
            text = "$health",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFC8E6C9)
        )
    }
}
