package com.farrah.sproutvision.ui

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.farrah.sproutvision.analyze.IdentifyConfidence
import com.farrah.sproutvision.analyze.IdentifySource
import com.farrah.sproutvision.analyze.PlantIdentification
import com.farrah.sproutvision.analyze.identifyBadgeText
import com.farrah.sproutvision.util.formatDateTime

/**
 * Wild-plant identification card. Its own screen — never the tray result
 * screen. Photo on top, names, confidence badge, then the care sheet.
 */
@Composable
fun IdentificationCard(
    result: PlantIdentification,
    photo: ImageBitmap?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            if (photo != null) {
                Image(
                    bitmap = photo,
                    contentDescription = "Identified plant photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)
                )
                Spacer(Modifier.height(12.dp))
            }
            Text(
                result.commonName,
                style = MaterialTheme.typography.headlineSmall
            )
            if (result.scientificName.isNotBlank()) {
                Text(
                    result.scientificName,
                    style = MaterialTheme.typography.titleMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (result.family.isNotBlank()) {
                Text(
                    "Family: ${result.family}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            ConfidenceBadge(confidence = result.confidence)
            Spacer(Modifier.height(8.dp))
            // Low confidence reads as a best guess AND says what photo
            // would help — a guess is never presented as certain.
            val tipLabel = if (result.confidence == IdentifyConfidence.LOW) {
                "Best guess — for a surer ID: ${result.betterPhotoTip}"
            } else {
                "Better photo tip: ${result.betterPhotoTip}"
            }
            Text(
                tipLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = if (result.confidence == IdentifyConfidence.LOW) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            if (result.isDemo) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Demo result — scripted, not a real identification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Source line so a screenshot proves which path ran. Unknown
                // (e.g. herbarium reopens, which predate it) shows nothing.
                val sourceLine = when (result.source) {
                    IdentifySource.META -> "Identified with Meta AI"
                    IdentifySource.GEMINI -> "Identified with Gemini"
                    IdentifySource.DEMO, null -> null
                }
                if (sourceLine != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        sourceLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Care sheet", style = MaterialTheme.typography.titleSmall)
            Text(
                if (result.source == IdentifySource.META) {
                    "A starting point from Meta AI's plant knowledge — verify for your area."
                } else {
                    "A starting point from Gemini's plant knowledge — verify for your area."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            CareRow(label = "Sun", value = result.care.sun)
            CareRow(label = "Water", value = result.care.water)
            CareRow(label = "Soil", value = result.care.soil)
            CareRow(label = "Hardiness zones", value = result.care.hardinessZones)
            CareRow(label = "Bloom time", value = result.care.bloomTime)
            CareRow(label = "Mature size", value = result.care.matureSize)
            if (result.care.funFact.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Did you know? ${result.care.funFact}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun CareRow(label: String, value: String) {
    if (value.isBlank()) return
    Text(
        "$label: $value",
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
fun ConfidenceBadge(confidence: IdentifyConfidence) {
    val color = when (confidence) {
        IdentifyConfidence.HIGH -> Color(0xFF2E7D32)
        IdentifyConfidence.MEDIUM -> Color(0xFFF9A825)
        IdentifyConfidence.LOW -> Color(0xFFC62828)
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            identifyBadgeText(confidence),
            color = color,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/**
 * HUD result for Identify mode: the card plus one-tap Save into the
 * herbarium. Nothing is written until Save is tapped — no auto-saving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentifyResultScreen(vm: SproutViewModel, result: PlantIdentification, onDone: () -> Unit) {
    val context = LocalContext.current
    var locationNote by remember { mutableStateOf("") }
    val bitmap = remember(vm.identifyPhoto) {
        val bytes = vm.identifyPhoto
        if (bytes == null || bytes.isEmpty()) null
        else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plant identification") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IdentificationCard(result = result, photo = bitmap, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = locationNote,
                onValueChange = { locationNote = it },
                label = { Text("Location note") },
                placeholder = { Text("e.g. Petrifying Springs, Oct 19") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    vm.saveIdentification(locationNote)
                    // Application context: a Toast on the panel activity
                    // context crashes on Quest (window type mismatch).
                    Toast.makeText(
                        context.applicationContext,
                        "Saved to herbarium",
                        Toast.LENGTH_SHORT
                    ).show()
                    onDone()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save to herbarium")
            }
            Button(
                onClick = {
                    vm.pinIdentification(locationNote)
                    Toast.makeText(
                        context.applicationContext,
                        "Saved — place your label",
                        Toast.LENGTH_SHORT
                    ).show()
                    onDone()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Pin label")
            }
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
        }
    }
}

/** Capture date line for a saved herbarium entry. */
@Composable
fun HerbariumDateLine(timestampEpochMs: Long) {
    Text(
        formatDateTime(timestampEpochMs),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
