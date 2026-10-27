package com.farrah.sproutvision.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.farrah.sproutvision.analyze.AnalysisResult
import com.farrah.sproutvision.data.TrayEntity

@Composable
fun AnalysisResultScreen(
    tray: TrayEntity,
    result: AnalysisResult?,
    error: String?,
    analyzing: Boolean,
    onApplyStage: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp)
    ) {
        Text("Scan result", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            tray.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        when {
            analyzing || (result == null && error == null) -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Analyzing tray…")
                }
            }
            error != null -> {
                Text(
                    "Analysis failed: $error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth()) {
                    Text("Back to tray")
                }
            }
            result != null -> {
                if (result.isDemo) {
                    Card(Modifier.fillMaxWidth()) {
                        Text(
                            "Demo result — scripted for emulator testing.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(result.variety, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    StageBadge(result.stage)
                }
                Spacer(Modifier.height(12.dp))

                Text("Health ${result.healthScore}/100")
                LinearProgressIndicator(
                    progress = { result.healthScore / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text("Canopy coverage ${result.coveragePercent}%")
                LinearProgressIndicator(
                    progress = { result.coveragePercent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                if (result.issues.isNotEmpty()) {
                    Text("Issues spotted", style = MaterialTheme.typography.titleSmall)
                    result.issues.forEach { issue ->
                        Text("• $issue", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                } else {
                    Text("No issues spotted.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }

                Text("Recommendation", style = MaterialTheme.typography.titleSmall)
                Text(result.recommendation, style = MaterialTheme.typography.bodyMedium)

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onApplyStage,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Set stage to ${result.stage.displayName}")
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) {
                        Text("Save analysis")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onDiscard, modifier = Modifier.weight(1f)) {
                        Text("Discard")
                    }
                }
            }
        }
    }
}
