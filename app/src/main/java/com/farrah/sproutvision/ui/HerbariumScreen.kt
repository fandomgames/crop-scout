package com.farrah.sproutvision.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.farrah.sproutvision.data.HerbariumEntity
import com.farrah.sproutvision.data.labelPinButton
import com.farrah.sproutvision.data.toIdentification

/**
 * Herbarium collection: grid of saved wild plants, tap to reopen the
 * full identification card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HerbariumGridScreen(
    entries: List<HerbariumEntity>,
    onOpen: (Long) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Herbarium") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close menu")
                    }
                }
            )
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No saved plants yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Identify a wild plant from the HUD, then tap Save.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    HerbariumGridCard(entry = entry, onClick = { onOpen(entry.id) })
                }
            }
        }
    }
}

@Composable
private fun HerbariumGridCard(entry: HerbariumEntity, onClick: () -> Unit) {
    val thumbnail = remember(entry.photoPath) {
        decodeHerbariumBitmap(entry.photoPath, sampleSize = 4)
    }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = entry.commonName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(entry.commonName, style = MaterialTheme.typography.titleSmall)
            if (entry.scientificName.isNotBlank()) {
                Text(
                    entry.scientificName,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Reopens a saved herbarium entry as the full identification card. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HerbariumDetailScreen(
    entry: HerbariumEntity,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit
) {
    val bitmap = remember(entry.photoPath) {
        decodeHerbariumBitmap(entry.photoPath, sampleSize = 1)
    }
    var deleteGate by remember(entry.id) { mutableStateOf<ConfirmGate<Unit>>(ConfirmGate.Idle) }
    var showUnpinConfirm by remember(entry.id) { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entry.commonName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close menu")
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
            IdentificationCard(
                result = entry.toIdentification(),
                photo = bitmap,
                modifier = Modifier.fillMaxWidth()
            )
            HerbariumDateLine(timestampEpochMs = entry.timestampEpochMs)
            if (entry.locationNote.isNotBlank()) {
                Text(
                    entry.locationNote,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            // Pin section: anchored entries offer Remove label (single
            // Cancel/Unpin confirm, mirroring the tray-card unlink);
            // unpinned entries offer Pin label to place it again.
            TextButton(
                onClick = { if (entry.hasAnchor()) showUnpinConfirm = true else onPin() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    labelPinButton(entry.hasAnchor()),
                    color = if (entry.hasAnchor()) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }
            if (showUnpinConfirm && entry.hasAnchor()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Remove this label?", style = MaterialTheme.typography.titleSmall)
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
                                Text("Unpin", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            if (deleteGate is ConfirmGate.Staged) {
                // Removal stages first and applies on a second pinch,
                // mirroring the tray-detail delete confirms.
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Remove this plant?", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "This removes ${entry.commonName} from the herbarium.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { deleteGate = cancelAct() }) { Text("Cancel") }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                val (act, next) = takeAct(deleteGate)
                                deleteGate = next
                                if (act != null) onDelete()
                            }) {
                                Text("Remove", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            } else {
                TextButton(onClick = { deleteGate = stageAct(Unit) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Remove from herbarium", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun decodeHerbariumBitmap(path: String?, sampleSize: Int) =
    if (path.isNullOrBlank()) null
    else BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply { inSampleSize = sampleSize }
    )?.asImageBitmap()
