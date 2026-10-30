package com.farrah.sproutvision.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.farrah.sproutvision.data.HerbariumEntity
import com.farrah.sproutvision.data.idLabelContentOf
import com.farrah.sproutvision.data.toIdentification
import com.farrah.sproutvision.ui.theme.SproutVisionTheme

/**
 * Root for one runtime ID-label panel. Mirrors [TrayCardRoute]: observes
 * the same ViewModel state, so pinning updates the label immediately.
 */
@Composable
fun IdLabelRoute(vm: SproutViewModel, entryId: Long) {
    val entries by vm.herbarium.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val entry = entries.firstOrNull { it.id == entryId } ?: return
    val ghost = vm.labelPlacementEntryId == entryId && !entry.hasAnchor()
    SproutVisionTheme(darkMode = settings.darkMode) {
        IdLabelPanel(
            entry = entry,
            ghost = ghost,
            expanded = !ghost && vm.expandedLabelEntryId == entryId,
            onToggleExpand = { vm.toggleLabelExpanded(entryId) },
            onDrop = { vm.dropLabelEntryId = entryId },
            onUnpin = {
                vm.clearHerbariumAnchor(entryId)
                if (vm.expandedLabelEntryId == entryId) vm.toggleLabelExpanded(entryId)
            }
        )
    }
}

@Composable
fun IdLabelPanel(
    entry: HerbariumEntity,
    ghost: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onDrop: () -> Unit,
    onUnpin: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Ghosts are translucent; tapping one drops it in place, grabbing moves it.
    Card(
        modifier = modifier.fillMaxSize()
            .then(if (ghost) Modifier.alpha(0.6f) else Modifier)
            .clickable(onClick = { if (ghost) onDrop() else onToggleExpand() })
    ) {
        if (ghost) {
            LabelGhostBody(entry)
        } else if (expanded) {
            LabelExpandedBody(entry, onUnpin)
        } else {
            LabelCompactBody(entry)
        }
    }
}

@Composable
private fun LabelCompactBody(entry: HerbariumEntity) {
    val content = remember(entry) { idLabelContentOf(entry) }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = content.commonName,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        if (content.scientificShort.isNotBlank()) {
            Text(
                text = content.scientificShort,
                fontSize = 16.sp,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = content.confidenceText,
            fontSize = 14.sp,
            color = if (content.isBestGuess) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Tap to expand",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LabelExpandedBody(entry: HerbariumEntity, onUnpin: () -> Unit) {
    // Reuses the existing card, care sheet, and date line — the entry was
    // already filed in the herbarium by the Pin tap, so no second save.
    val bitmap = remember(entry.photoPath) {
        if (entry.photoPath.isNullOrBlank()) null
        else BitmapFactory.decodeFile(entry.photoPath)?.asImageBitmap()
    }
    var showUnpinConfirm by remember(entry.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IdentificationCard(
            result = entry.toIdentification(),
            photo = bitmap,
            modifier = Modifier.fillMaxWidth()
        )
        HerbariumDateLine(timestampEpochMs = entry.timestampEpochMs)
        Text(
            text = "Saved in herbarium",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Single confirm card mirroring the tray-card unlink: Cancel or
        // Remove label, not double-gated. It swaps into the button's spot
        // instead of rendering below it — the expanded card is taller than
        // the panel, so a below-the-button confirm lands off-screen.
        if (showUnpinConfirm) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Remove this label?",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "Unpins the floating label. The herbarium entry — " +
                            "photo, ID, and care sheet — stays.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { showUnpinConfirm = false }) {
                            Text("Cancel")
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { showUnpinConfirm = false; onUnpin() }) {
                            Text("Remove label", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        } else {
            TextButton(onClick = { showUnpinConfirm = true }) {
                Text("Remove label", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun LabelGhostBody(entry: HerbariumEntity) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = entry.commonName,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Tap to drop here · or grab to move",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
